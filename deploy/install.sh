#!/usr/bin/env bash
# Sets up the host and installs a production release. Run as root by the
# production SSM deploy document, from the unpacked release package:
#   deploy/install.sh
#
# This is the only script that changes the host: packages, the service users,
# the systemd unit, both environments' settings, nginx and the certificate.
# Staging's deploy document only swaps staging's jar (infra/host.yaml), so
# staging can't change anything production runs on. A change here reaches the
# host with the next production deploy.
#
# Safe to run again: each step checks before it changes anything.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
domain=apps.tauasa.org

log() { echo "[install] $*"; }

# 1. Packages: Java, nginx and certbot. Installed once.
if ! command -v java >/dev/null || ! command -v nginx >/dev/null || ! command -v certbot >/dev/null; then
  log "installing packages"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -q
  apt-get install -y -q openjdk-17-jre-headless nginx certbot
  rm -f /etc/nginx/sites-enabled/default
fi

# 2. Each environment runs as its own user, with no shell and no home, owning nothing.
for env in staging production; do
  user="faux-person-web-$env"
  if ! id "$user" >/dev/null 2>&1; then
    log "adding the $user user"
    useradd --system --no-create-home --shell /usr/sbin/nologin "$user"
  fi
done

# 3. The unit, each environment's settings, and a drop-in that lets each bind
#    only its own port. Staging also gets at most half of one CPU's time, so
#    traffic to staging can't use up the CPU (and CPU credits) production needs.
install -m 0755 -d /etc/faux-person-web /opt/faux-person-web/staging /opt/faux-person-web/production
install -m 0644 "$here/systemd/faux-person-web@.service" /etc/systemd/system/faux-person-web@.service
for pair in staging:8081:50% production:8080:; do
  IFS=: read -r env port quota <<< "$pair"
  install -m 0644 "$here/env/$env.env" "/etc/faux-person-web/$env.env"
  install -m 0755 -d "/etc/systemd/system/faux-person-web@$env.service.d"
  {
    printf '[Service]\nSocketBindAllow=tcp:%s\nSocketBindDeny=any\n' "$port"
    if [ -n "$quota" ]; then printf 'CPUQuota=%s\n' "$quota"; fi
  } > "/etc/systemd/system/faux-person-web@$env.service.d/limits.conf"
  rm -f "/etc/systemd/system/faux-person-web@$env.service.d/port.conf"
done
systemctl daemon-reload

# 4. nginx, and the certificate. Until the certificate exists, nginx serves only
#    Let's Encrypt's checks. certbot renews it on its own timer and reloads nginx.
mkdir -p /var/www/letsencrypt
install -m 0755 -d /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\nsystemctl reload nginx\n' > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx
chmod 0755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx
systemctl enable --now nginx
if [ ! -s "/etc/letsencrypt/live/$domain/fullchain.pem" ]; then
  log "getting the TLS certificate for $domain"
  install -m 0644 "$here/nginx/apps.tauasa.org.http.conf" "/etc/nginx/conf.d/$domain.conf"
  nginx -t && systemctl reload nginx
  certbot certonly --webroot -w /var/www/letsencrypt -d "$domain" \
    --non-interactive --agree-tos --register-unsafely-without-email
fi
# Checked before it replaces the working config, so a bad file can't take nginx down.
install -m 0644 "$here/nginx/apps.tauasa.org.conf" "/etc/nginx/conf.d/$domain.conf.new"
cp -p "/etc/nginx/conf.d/$domain.conf" "/etc/nginx/conf.d/$domain.conf.bak" 2>/dev/null || true
mv "/etc/nginx/conf.d/$domain.conf.new" "/etc/nginx/conf.d/$domain.conf"
if ! nginx -t; then
  log "the new nginx config failed its check; putting the old one back"
  mv "/etc/nginx/conf.d/$domain.conf.bak" "/etc/nginx/conf.d/$domain.conf"
  exit 1
fi
rm -f "/etc/nginx/conf.d/$domain.conf.bak"
systemctl reload nginx

# 5. The production release. The one it replaces is kept beside it as .previous.
app_dir=/opt/faux-person-web/production
log "installing the production jar"
if [ -f "$app_dir/faux-person-web.jar" ]; then
  cp -p "$app_dir/faux-person-web.jar" "$app_dir/faux-person-web.jar.previous"
fi
install -m 0644 "$here/../faux-person-web.jar" "$app_dir/faux-person-web.jar"
systemctl enable faux-person-web@production >/dev/null
systemctl restart faux-person-web@production

# 6. Up? /health on the app's own port, for up to a minute.
for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/faux-person-web/health >/dev/null 2>&1; then
    log "production is healthy"
    exit 0
  fi
  sleep 1
done
log "production isn't healthy after 60 seconds; the last log lines:"
journalctl -u faux-person-web@production -n 40 --no-pager || true
exit 1
