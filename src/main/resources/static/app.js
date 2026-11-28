// faux-person-web's page script. The page works without it; it adds what a
// plain form can't do: the Random button, the seed history, the Copy buttons, a Zip
// checkbox that's off for HTML, where it does nothing, and a row's card on hover.
// The seed history stays in this browser (localStorage). The server never sees it.
(function () {
  "use strict";

  var MAX_SEEDS = 20;
  var kind = document.body.getAttribute("data-kind");
  var seedBox = document.getElementById("seed");
  var history = document.getElementById("seed-history");
  var random = document.getElementById("random");
  var shown = document.getElementById("shown-seed");
  var storeKey = "faux-person-web.seeds." + kind;

  function readSeeds() {
    try {
      var seeds = JSON.parse(window.localStorage.getItem(storeKey) || "[]");
      return Array.isArray(seeds) ? seeds.filter(function (s) { return /^-?\d+$/.test(s); }) : [];
    } catch (e) {
      return [];
    }
  }

  function writeSeeds(seeds) {
    try {
      window.localStorage.setItem(storeKey, JSON.stringify(seeds.slice(0, MAX_SEEDS)));
    } catch (e) {
      // Storage blocked or full: the history just isn't kept.
    }
  }

  // Remember the seed behind the rows on screen, newest first.
  if (shown && /^-?\d+$/.test(shown.textContent)) {
    var seed = shown.textContent;
    writeSeeds([seed].concat(readSeeds().filter(function (s) { return s !== seed; })));
  }

  if (history) {
    readSeeds().forEach(function (s) {
      var option = document.createElement("option");
      option.value = s;
      history.appendChild(option);
    });
  }

  if (random && seedBox) {
    random.hidden = false;
    random.addEventListener("click", function () {
      seedBox.value = "";
      seedBox.focus();
    });
  }

  var format = document.getElementById("format");
  var zip = document.getElementById("zip");
  if (format && zip) {
    // Ticked by default, so a file comes with its data credit. Off for HTML,
    // and back to the person's last choice for any other format.
    var wantZip = zip.defaultChecked;
    zip.addEventListener("change", function () {
      wantZip = zip.checked;
    });
    var syncZip = function () {
      zip.disabled = format.value === "html";
      zip.checked = !zip.disabled && wantZip;
    };
    format.addEventListener("change", syncZip);
    // Going back to the page can restore another format after this script ran.
    window.addEventListener("pageshow", syncZip);
    syncZip();
  }

  var copySeed = document.getElementById("copy-seed");
  var seedCopied = document.getElementById("seed-copied");
  if (copySeed && seedCopied && shown && navigator.clipboard) {
    var seedTimer;
    copySeed.hidden = false;
    copySeed.addEventListener("click", function () {
      navigator.clipboard.writeText(shown.textContent).then(function () {
        copySeed.classList.add("copied");
        copySeed.title = "Copied";
        seedCopied.textContent = "Seed copied";
        window.clearTimeout(seedTimer);
        seedTimer = window.setTimeout(function () {
          copySeed.classList.remove("copied");
          copySeed.title = "Copy seed";
          seedCopied.textContent = "";
        }, 1500);
      });
    });
  }

  var copy = document.getElementById("copy-curl");
  var curl = document.getElementById("curl");
  if (copy && curl && navigator.clipboard) {
    copy.hidden = false;
    copy.addEventListener("click", function () {
      navigator.clipboard.writeText(curl.textContent).then(function () {
        copy.textContent = "Copied";
        window.setTimeout(function () { copy.textContent = "Copy"; }, 1500);
      });
    });
  }

  // Resting the mouse on a row for a second shows it in a card, one field per line,
  // with a Copy button. Touch has no hover, so it's mouse only.
  var HOVER_MS = 1000;
  var LEAVE_MS = 300;
  var tbody = document.querySelector(".table-wrap tbody");
  if (tbody) {
    var titles = Array.prototype.map.call(document.querySelectorAll(".table-wrap thead th"), function (th) {
      var title = th.querySelector("a span");
      return (title || th).textContent.trim();
    });
    var card = document.createElement("div");
    card.className = "row-card";
    card.hidden = true;
    card.setAttribute("role", "group");
    card.setAttribute("aria-label", "Row details");
    var fields = document.createElement("dl");
    var copyRow = document.createElement("button");
    copyRow.type = "button";
    copyRow.className = "primary";
    copyRow.textContent = "Copy";
    copyRow.hidden = !navigator.clipboard;
    var rowCopied = document.createElement("span");
    rowCopied.className = "sr-only";
    rowCopied.setAttribute("role", "status");
    card.appendChild(fields);
    card.appendChild(copyRow);
    card.appendChild(rowCopied);
    document.body.appendChild(card);

    var showTimer, hideTimer, copyTimer, pending, shownRow, pointerX = 0, text = "";

    var lines = function (row) {
      return Array.prototype.map.call(row.cells, function (cell, i) {
        return { title: titles[i] || "Column " + (i + 1), value: cell.textContent, dir: cell.getAttribute("dir") };
      });
    };

    var hide = function () {
      window.clearTimeout(hideTimer);
      if (card.contains(document.activeElement)) {
        document.activeElement.blur();
      }
      card.hidden = true;
      if (shownRow) {
        shownRow.classList.remove("row-shown");
        shownRow = null;
      }
    };

    // Escape, a click or a resize closes the card and keeps it closed until the
    // mouse moves to another row.
    var dismissed = null;
    var dismiss = function () {
      dismissed = shownRow || pending;
      window.clearTimeout(showTimer);
      pending = null;
      hide();
    };

    var show = function (row) {
      hide();
      fields.textContent = "";
      text = lines(row).map(function (line) {
        var dt = document.createElement("dt");
        var dd = document.createElement("dd");
        dt.textContent = line.title;
        dd.textContent = line.value;
        // A native name keeps its direction in the card too: right to left for Hebrew or Arabic.
        if (line.dir) {
          dd.dir = line.dir;
        }
        fields.appendChild(dt);
        fields.appendChild(dd);
        return line.title + ": " + line.value;
      }).join("\n");
      window.clearTimeout(copyTimer);
      copyRow.textContent = "Copy";
      rowCopied.textContent = "";
      shownRow = row;
      row.classList.add("row-shown");
      var viewWidth = document.documentElement.clientWidth;
      card.style.maxWidth = (viewWidth - 16) + "px";
      card.hidden = false;
      // Under the row, starting near the pointer; above it if there's no room below.
      // With room on neither side, it stays inside the window and covers part of the row.
      var box = row.getBoundingClientRect();
      var width = card.offsetWidth;
      var height = card.offsetHeight;
      var left = Math.max(8, Math.min(pointerX - 24, viewWidth - width - 8));
      // The site header sticks to the top on wide screens; the card mustn't go under it.
      var header = document.querySelector(".site-header");
      var ceiling = header && window.getComputedStyle(header).position === "sticky"
        ? header.getBoundingClientRect().bottom : 0;
      var top = box.bottom;
      if (top + height > window.innerHeight) {
        top = box.top - height >= ceiling ? box.top - height
          : Math.max(ceiling + 8, window.innerHeight - height - 8);
      }
      card.style.left = (left + window.scrollX) + "px";
      card.style.top = (top + window.scrollY) + "px";
    };

    var later = function () {
      window.clearTimeout(hideTimer);
      hideTimer = window.setTimeout(hide, LEAVE_MS);
    };

    tbody.addEventListener("pointerover", function (e) {
      if (e.pointerType !== "mouse") {
        return;
      }
      var row = e.target.closest("tr");
      pointerX = e.clientX;
      // Dragging to select text doesn't open a card.
      if (e.buttons !== 0) {
        window.clearTimeout(showTimer);
        pending = null;
        return;
      }
      if (row === shownRow) {
        window.clearTimeout(hideTimer);
        window.clearTimeout(showTimer);
        pending = null;
        return;
      }
      if (row === pending || row === dismissed) {
        return;
      }
      dismissed = null;
      window.clearTimeout(showTimer);
      pending = row;
      if (shownRow) {
        later();
      }
      showTimer = window.setTimeout(function () {
        pending = null;
        if (row.isConnected) {
          show(row);
        }
      }, HOVER_MS);
    });
    tbody.addEventListener("pointermove", function (e) {
      pointerX = e.clientX;
    });
    tbody.addEventListener("pointerleave", function () {
      window.clearTimeout(showTimer);
      pending = null;
      dismissed = null;
      if (shownRow) {
        later();
      }
    });
    // Moving from the row onto the card keeps it open; leaving the card closes it.
    card.addEventListener("pointerenter", function () {
      window.clearTimeout(hideTimer);
      window.clearTimeout(showTimer);
      pending = null;
    });
    card.addEventListener("pointerleave", later);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        dismiss();
      }
    });
    document.addEventListener("pointerdown", function (e) {
      if (!card.contains(e.target)) {
        dismiss();
      }
    });
    window.addEventListener("resize", dismiss);

    var copyDone = function (label, status) {
      copyRow.textContent = label;
      rowCopied.textContent = status;
      window.clearTimeout(copyTimer);
      copyTimer = window.setTimeout(function () {
        copyRow.textContent = "Copy";
        rowCopied.textContent = "";
      }, 1500);
    };
    copyRow.addEventListener("click", function () {
      navigator.clipboard.writeText(text).then(function () {
        copyDone("Copied", "Row copied");
      }, function () {
        copyDone("Copy failed", "Copy failed");
      });
    });
  }
})();
