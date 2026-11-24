# faux-person-web's infrastructure

[`host.yaml`](host.yaml) is one CloudFormation stack, `faux-person-web-host`,
in account 027934732805, `us-east-1`. It holds:

- the instance (`t4g.small`, Ubuntu 24.04 arm64, IMDSv2 only, encrypted disk,
  standard CPU credits)
- its Elastic IP, and `apps.tauasa.org` pointing at it in tauasa.org's Route 53 zone
- a security group open on 80 and 443 only (IPv4), with no SSH (use Systems Manager)
- a private bucket for releases, which expire after 30 days
- for staging and for production: an SSM deploy document, and a GitHub deploy
  role that can write only that environment's releases and run only that
  environment's document

**What each deploy may do on the instance.** Staging's document runs only the
commands written in `host.yaml`: it swaps staging's jar and restarts staging,
nothing else. Production's document runs the release's `deploy/install.sh`,
which also sets up the host: packages, the service users, the systemd unit,
both environments' settings, nginx and the certificate. So staging can't
change anything production runs on, and the host changes only on a
production deploy, which a person starts.

The stack is applied by hand, never by CI. CI can deploy code, not change
infrastructure.

**Cost**, in `us-east-1`, running all month:

| Item | About |
|---|---|
| `t4g.small` instance | $12.30 |
| Elastic IP (AWS charges for every public IPv4 address) | $3.65 |
| 16 GB gp3 disk | $1.30 |
| Release bucket and DNS queries | a few cents |
| **Total** | **$17.30 a month** |

## One-time setup

Run these with the AWS CLI profile for the account. Confirm which account it
reaches first:

```bash
aws sts get-caller-identity --profile tauasa
```

1. **Check GitHub's OIDC provider exists.** The deploy roles trust the
   account's existing provider for `token.actions.githubusercontent.com`. In
   this account it belongs to the `tsj-github-bootstrap` stack, which another
   project created. This stack doesn't create one, and without it every deploy
   fails at signing in to AWS.

   ```bash
   aws iam list-open-id-connect-providers --profile tauasa \
     --query 'OpenIDConnectProviderList[].Arn' --output text | tr '\t' '\n' | grep token.actions.githubusercontent.com
   ```

   No output means it's missing. tauasa.org's `infra/bootstrap.yaml` with
   `CreateOidcProvider=true` shows how to make one; don't create a second if
   one exists.

2. **Look up the image and the DNS zone.**

   ```bash
   aws ssm get-parameter --profile tauasa --region us-east-1 \
     --name /aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id \
     --query Parameter.Value --output text
   aws route53 list-hosted-zones-by-name --profile tauasa --dns-name tauasa.org \
     --query 'HostedZones[0].Id' --output text
   ```

   The zone ID is the part after `/hostedzone/`.

3. **Create the stack.**

   ```bash
   aws cloudformation deploy --profile tauasa --region us-east-1 \
     --stack-name faux-person-web-host --template-file infra/host.yaml \
     --capabilities CAPABILITY_IAM \
     --parameter-overrides ImageId=<ami-id> HostedZoneId=<zone-id>
   ```

   The image ID stays as given until you pass another one, so an update never
   replaces the instance by surprise. A new image ID replaces the instance;
   the next production deploy then sets the new one up from scratch, and
   staging's next deploy follows.

4. **Read the outputs.**

   ```bash
   aws cloudformation describe-stacks --profile tauasa --region us-east-1 \
     --stack-name faux-person-web-host --query 'Stacks[0].Outputs' --output table
   ```

5. **Create the GitHub environments** `staging` and `production` in the
   repository's settings. Give each these variables, from the outputs:

   | Variable | staging | production |
   |---|---|---|
   | `AWS_DEPLOY_ROLE_ARN` | `StagingDeployRoleArn` | `ProductionDeployRoleArn` |
   | `DEPLOY_DOCUMENT` | `StagingDeployDocument` | `ProductionDeployDocument` |
   | `RELEASE_BUCKET` | `ReleaseBucket` | `ReleaseBucket` |
   | `INSTANCE_ID` | `InstanceId` | `InstanceId` |
   | `APP_URL` | `StagingUrl` | `ProductionUrl` |

   Limit **both** environments to the `main` branch (Deployment branches and
   tags → Selected branches → `main`). The deploy roles trust any workflow
   that names their environment, so this rule is what stops a workflow on
   another branch from deploying. For production, you can also add yourself
   as a required reviewer, so each production deploy waits for your approval
   as well as the typed phrase.

   Each deploy role trusts only the token GitHub issues to this repository's
   matching environment. The repository uses GitHub's immutable subject form,
   `repo:tauasa@205476/faux-person-web@1468140622:environment:<name>`, so a
   repository renamed or re-created under the same name can't use the roles.
   The stack's `GitHubSubjectPrefix` holds it; check it with
   `gh api repos/tauasa/faux-person-web/actions/oidc/customization/sub`.

6. **Deploy production first**: Actions → *deploy-production* → *Run
   workflow* on `main`, typing `deploy to production`. Production's deploy
   sets up the host: it installs Java, nginx and certbot, adds the service
   users, and gets the Let's Encrypt certificate, so the first one takes a few
   minutes. It needs `apps.tauasa.org` to resolve to the instance, which the
   stack's record does within a few minutes of step 3. Check with
   `dig +short apps.tauasa.org`.

7. **Then staging**: run *deploy-staging* from the Actions tab, or merge to
   `main`. Before step 6 it fails with "The host is not set up".

## Changing a setting on the server

The app's settings (SPEC.md, Configuration) are environment variables in
`deploy/env/production.env` and `deploy/env/staging.env`. Add one there, for
example `FAUX_WEB_MAX_COUNT=2000`, merge, and run a production deploy: it
installs both files. Staging picks up its file at its next restart, which its
next deploy does.

## Reaching the instance

There's no SSH. Open a shell through Systems Manager:

```bash
aws ssm start-session --profile tauasa --region us-east-1 --target <InstanceId>
```

Useful there:
- `systemctl status faux-person-web@production`
- `journalctl -u faux-person-web@staging -n 100`
- `sudo nginx -t`

**Check the certificate now and then.** certbot renews it on a timer, but it
was registered without an email address, so nobody is told if a renewal
fails, and with HSTS a lapsed certificate locks browsers out. Run
`sudo certbot certificates` to see its expiry date, and
`systemctl list-timers certbot.timer` to see the next renewal. A dry run
checks renewal works: `sudo certbot renew --dry-run`.

## Rolling back

- **A bad release:** revert the change on `main`, then run *deploy-production*
  (staging redeploys on the merge).
- **Faster, on the instance:** each environment keeps the release before the
  current one beside it. For production:

  ```bash
  sudo cp -p /opt/faux-person-web/production/faux-person-web.jar.previous \
            /opt/faux-person-web/production/faux-person-web.jar
  sudo systemctl restart faux-person-web@production
  ```

  The next deploy replaces it, so revert the code too.
- **The stack:** CloudFormation rolls a failed update back on its own. Never
  delete the stack to undo something: deleting it releases the Elastic IP and
  the DNS record goes with it.

The bucket keeps each release 30 days for reference; no workflow redeploys
one from it. There's no data to restore: the app stores nothing.

## Retiring it

Deleting the stack removes everything in it, the bucket included once it's
empty. Empty the bucket first:
`aws s3 rm --recursive s3://<ReleaseBucket> --profile tauasa`.
