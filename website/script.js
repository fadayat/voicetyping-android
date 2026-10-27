const menuButton = document.querySelector(".menu-button");
const navigation = document.querySelector(".primary-nav");
const demoButton = document.querySelector("#voice-demo");
const demoPhone = document.querySelector(".phone");
const demoText = document.querySelector("#demo-text");
const demoStatus = document.querySelector("#demo-status");

requestAnimationFrame(() => document.body.classList.add("page-ready"));

if (menuButton && navigation) {
  const closeMenu = () => {
    navigation.classList.remove("is-open");
    menuButton.setAttribute("aria-expanded", "false");
  };

  menuButton.addEventListener("click", () => {
    const isOpen = navigation.classList.toggle("is-open");
    menuButton.setAttribute("aria-expanded", String(isOpen));
  });

  navigation.addEventListener("click", (event) => {
    if (event.target instanceof HTMLAnchorElement) closeMenu();
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeMenu();
  });
}

if (demoButton && demoPhone && demoText && demoStatus) {
  let demoTimers = [];

  const clearDemoTimers = () => {
    demoTimers.forEach(window.clearTimeout);
    demoTimers = [];
  };

  const setDemoState = (state, text, status) => {
    demoPhone.classList.remove("is-listening", "is-writing");
    if (state) demoPhone.classList.add(state);
    demoText.textContent = text;
    demoStatus.textContent = status;
  };

  demoButton.addEventListener("click", () => {
    clearDemoTimers();
    demoButton.setAttribute("aria-label", "Voice preview is listening");
    setDemoState("is-listening", "Listening…", "Listening");

    demoTimers.push(window.setTimeout(() => {
      demoButton.setAttribute("aria-label", "Voice preview is preparing text");
      setDemoState("is-writing", "Preparing text…", "Writing");
    }, 1350));

    demoTimers.push(window.setTimeout(() => {
      demoButton.setAttribute("aria-label", "Replay interactive voice typing preview");
      setDemoState("", "Sabah saat onda görüşək.", "Inserted");
    }, 2300));
  });
}

const year = document.querySelector("#year");
if (year) year.textContent = String(new Date().getFullYear());
