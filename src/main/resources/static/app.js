// faux-person-web's page script. The page works without it; it adds what a
// plain form can't do: the Random button, the seed history and the Copy buttons.
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
})();
