const COMMERCE_HEADER = document.querySelector(".o-header-commerce");
const NAVIGATION_WRAPPER = COMMERCE_HEADER?.querySelector(
  ".o-header-commerce__main-navigation__content__wrapper"
);
const NAVIGATION_CONTENT = COMMERCE_HEADER?.querySelector(
  ".o-header-commerce__main-navigation__content"
);
const SUPERGRAPHIC = COMMERCE_HEADER?.querySelector(
  "div.o-header-commerce__supergraphic"
);
const PREV_LEVEL_BUTTON = COMMERCE_HEADER?.querySelector(
  "#previous-level-button"
);
const MENU_CLOSE_BUTTON = COMMERCE_HEADER?.querySelector("#menu-close-button");
const MENU_CLOSE_BUTTON_DESKTOP = COMMERCE_HEADER?.querySelector(
  "#menu-close-button__desktop"
);
const MENU_CLOSE_BUTTON_DESKTOP_WRAPPER = COMMERCE_HEADER?.querySelector(".o-header-commerce__main-navigation__close__button");
const NAVIGATION_LEVELS = ["-level-0", "-level-1", "-level-2", "-level-3"];
const UTILITY_BAR = COMMERCE_HEADER?.querySelector(
  ".o-header-commerce__utility-bar"
);
const UTILITY_BAR_MOBILE = COMMERCE_HEADER?.querySelector(
  "#utility_bar_mobile"
);
const TAB_NAVIGATION = document.querySelector(".a-tab-navigation");
const TAB_NAVIGATION_BUTTONS = Array.from(
  COMMERCE_HEADER?.querySelectorAll(".a-tab-navigation__tab") || []
);
const PRESENTATION_ELEMENTS = Array.from(NAVIGATION_CONTENT?.querySelectorAll(
  ".o-header-commerce__main-navigation__content__teaser"
) || []);
const clearTabs = () => {
  TAB_NAVIGATION_BUTTONS.forEach((btn) => {
    btn.classList.remove("-selected", "-expanded");
    btn.setAttribute("aria-expanded", "false");
  });
};
const clearHighlights = () => {
  const highlightElements = NAVIGATION_WRAPPER?.querySelectorAll(".-expanded");
  highlightElements?.forEach((element) => {
    element.classList.remove("-expanded");
  });
};
const clearVisible = () => {
  const visibleElements = NAVIGATION_WRAPPER?.querySelectorAll(".-visible");
  visibleElements?.forEach((element) => {
    element.classList.remove("-visible");
  });
};
const clearTeasers = () => {
  if (MENU_CLOSE_BUTTON_DESKTOP_WRAPPER) {
    MENU_CLOSE_BUTTON_DESKTOP_WRAPPER.classList.remove("-secondary");
  }
  PRESENTATION_ELEMENTS.forEach((element) => {
    element.classList.remove("-visible");
  });
};
const clearLevelClasses = () => {
  NAVIGATION_LEVELS.forEach((levelClass) => {
    NAVIGATION_WRAPPER?.classList.remove(levelClass);
  });
};
const openMenu = () => {
  if (NAVIGATION_WRAPPER) {
    NAVIGATION_WRAPPER.classList.add("-open");
    NAVIGATION_WRAPPER.classList.add("-floating-shadow-m");
  }
  if (NAVIGATION_CONTENT) {
    NAVIGATION_CONTENT.classList.add("-visible");
  }
};
const closeMenu = () => {
  const activeTab = TAB_NAVIGATION_BUTTONS.find(
    (btn) => btn.classList.contains("-selected")
  );
  NAVIGATION_CONTENT?.classList.remove("-visible");
  NAVIGATION_WRAPPER?.classList.remove("-open");
  NAVIGATION_WRAPPER?.classList.remove("-floating-shadow-m");
  clearLevelClasses();
  clearVisible();
  clearHighlights();
  clearTabs();
  clearTeasers();
  if (activeTab) activeTab.focus();
};
const goToPrevLevel = () => {
  if (!NAVIGATION_WRAPPER) return;
  clearTeasers();
  let currentLevel = "";
  const levelMatch = Array.from(NAVIGATION_WRAPPER.classList).find(
    (cls) => cls.match(/^-level-\d+$/)
  );
  if (levelMatch) {
    currentLevel = levelMatch;
    const levelNumber = Number(levelMatch.replace("-level-", ""));
    if (levelNumber === 1 && PREV_LEVEL_BUTTON) {
      PREV_LEVEL_BUTTON.style.visibility = "hidden";
    }
    NAVIGATION_WRAPPER.classList.remove(levelMatch);
    NAVIGATION_WRAPPER.classList.add(`-level-${levelNumber - 1}`);
    NAVIGATION_WRAPPER.classList.remove("-with-teaser");
    const currentLevelElements = NAVIGATION_WRAPPER.querySelectorAll(
      `.${currentLevel}`
    );
    currentLevelElements.forEach((element) => {
      element.classList.remove("-visible");
    });
    const highlightElements = NAVIGATION_CONTENT?.querySelectorAll(
      ".o-header-commerce__main-navigation__content__links button.-expanded"
    );
    highlightElements?.forEach((highlightEl) => {
      highlightEl.classList.remove("-expanded");
    });
  }
};
const unsetHeight = () => {
  if (NAVIGATION_CONTENT) {
    if (NAVIGATION_CONTENT.style.height) {
      NAVIGATION_CONTENT.style.height = "";
    }
  }
};
let currentActiveTab = null;
const handleAriaControlsClick = (event, level = 0) => {
  if (!NAVIGATION_WRAPPER || !NAVIGATION_CONTENT) return;
  const clickedButton = event.currentTarget;
  if (currentActiveTab === clickedButton && NAVIGATION_WRAPPER.classList.contains("-open") && clickedButton?.classList.contains("-selected") && clickedButton?.getAttribute("aria-expanded") === "true") {
    closeMenu();
    return;
  }
  const controlsId = clickedButton?.getAttribute("aria-controls");
  openMenu();
  clearLevelClasses();
  const hasTeaser = clickedButton.getAttribute("aria-details") === "teaser";
  NAVIGATION_WRAPPER.classList.add(`-level-${level}`);
  if (hasTeaser) {
    NAVIGATION_WRAPPER.classList.add(`-with-teaser`);
    MENU_CLOSE_BUTTON_DESKTOP?.parentElement?.classList.add("-main");
  } else {
    NAVIGATION_WRAPPER.classList.remove(`-with-teaser`);
    MENU_CLOSE_BUTTON_DESKTOP?.parentElement?.classList.remove("-main");
  }
  if (PREV_LEVEL_BUTTON && level === 0) {
    PREV_LEVEL_BUTTON.style.visibility = "hidden";
  }
  const newButtons = NAVIGATION_CONTENT.querySelectorAll(
    `#${controlsId} button`
  );
  const parentLi = clickedButton.closest("li");
  if (parentLi) {
    const siblings = Array.from(parentLi.parentElement?.children || []).filter(
      (child) => child !== parentLi && child.tagName === "LI"
    );
    siblings.forEach((sibling) => {
      const siblingButtons = sibling.querySelectorAll(
        "button"
      );
      [...siblingButtons, ...newButtons].forEach((btn) => {
        btn.classList.remove("-expanded");
        btn.setAttribute("aria-expanded", "false");
      });
    });
  }
  if (!clickedButton.classList.contains("a-tab-navigation__tab")) {
    clickedButton.classList.add("-expanded");
  }
  clickedButton.setAttribute("aria-expanded", "true");
  const visibleUls = NAVIGATION_CONTENT.querySelectorAll("ul.-visible");
  visibleUls.forEach((ul) => {
    const isParent = ul.contains(clickedButton);
    const isSibling = ul.parentElement === clickedButton.parentElement;
    if (!isParent && !isSibling) {
      ul.classList.remove("-visible");
    }
  });
  unsetHeight();
  clearTeasers();
  const targetElement = document.getElementById(controlsId);
  if (controlsId) {
    if (targetElement) {
      targetElement.classList.add("-visible");
    }
  }
  if (PRESENTATION_ELEMENTS.some((element) => element.classList.contains("-visible"))) {
    MENU_CLOSE_BUTTON_DESKTOP_WRAPPER.classList.add("-secondary");
  }
  const visibleElements = NAVIGATION_CONTENT.querySelectorAll("ul.-visible");
  if (visibleElements.length > 1 && window.innerWidth > 991) {
    const maxHeight = Math.max(
      ...Array.from(visibleElements).map(
        (el) => el.offsetHeight
      )
    );
    if (maxHeight === visibleElements[0].offsetHeight) {
      unsetHeight();
      return;
    }
    if (maxHeight > 0) {
      NAVIGATION_CONTENT.style.height = `calc(${maxHeight}px + 3rem)`;
    }
  } else {
    unsetHeight();
  }
  if (targetElement) {
    const elementToFocus = targetElement.querySelector("li > *");
    if (elementToFocus) {
      if (elementToFocus.tagName === "BUTTON") {
        elementToFocus.focus();
      } else {
        elementToFocus.firstChild.focus();
      }
    }
  }
};
const setupButtonsListeners = () => {
  const buttonsLevel0 = Array.from(COMMERCE_HEADER?.querySelectorAll(".-open-level-0") || []);
  const buttonsLevel1 = Array.from(COMMERCE_HEADER?.querySelectorAll(".-open-level-1") || []);
  const buttonsLevel2 = Array.from(COMMERCE_HEADER?.querySelectorAll(".-open-level-2") || []);
  const buttonsLevel3 = Array.from(COMMERCE_HEADER?.querySelectorAll(".-open-level-3") || []);
  buttonsLevel0.forEach((btn) => {
    btn.addEventListener("click", (e) => handleAriaControlsClick(e, 0));
  });
  buttonsLevel1.forEach((btn) => {
    btn.addEventListener("click", (e) => handleAriaControlsClick(e, 1));
  });
  buttonsLevel2.forEach((btn) => {
    btn.addEventListener("click", (e) => handleAriaControlsClick(e, 2));
  });
  buttonsLevel3.forEach((btn) => {
    btn.addEventListener("click", (e) => handleAriaControlsClick(e, 3));
  });
  PREV_LEVEL_BUTTON?.addEventListener("click", goToPrevLevel);
  MENU_CLOSE_BUTTON?.addEventListener("click", closeMenu);
  MENU_CLOSE_BUTTON_DESKTOP?.addEventListener("click", closeMenu);
};
document.addEventListener("DOMContentLoaded", () => {
  setupButtonsListeners();
});
document.addEventListener("bosch.frontend-kit-done", () => {
  if (TAB_NAVIGATION) {
    TAB_NAVIGATION.component.setScrollPadding(0);
  }
  const UTILITY_SPLIT_BUTTON_MOBILE = document.querySelector("#utility_bar_mobile .o-split-button");
  if (UTILITY_SPLIT_BUTTON_MOBILE) {
    UTILITY_SPLIT_BUTTON_MOBILE.component.setRecalculatePopoverPositionOnOpen(true);
    UTILITY_SPLIT_BUTTON_MOBILE.component.popover.attach(
      UTILITY_SPLIT_BUTTON_MOBILE,
      document.querySelector(".o-header-commerce")
    );
  }
});
document.addEventListener("click", (event) => {
  if (COMMERCE_HEADER && !COMMERCE_HEADER.contains(event.target)) {
    closeMenu();
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeMenu();
  }
});
const handleScroll = () => {
  if (!COMMERCE_HEADER || !SUPERGRAPHIC) return;
  let scrolledMoreClass = "-scrolled-more";
  let scrolledMoreValue = 95;
  if (!UTILITY_BAR) {
    scrolledMoreClass = "-scrolled-more__without-utility-bar";
    scrolledMoreValue = 80;
  }
  if (window.scrollY >= scrolledMoreValue && window.innerWidth > 1024) {
    COMMERCE_HEADER.classList.add(scrolledMoreClass);
    COMMERCE_HEADER.classList.add("-floating-shadow-m");
    SUPERGRAPHIC.classList.add("-scrolled-more");
  } else {
    COMMERCE_HEADER.classList.remove(scrolledMoreClass);
    COMMERCE_HEADER.classList.remove("-floating-shadow-m");
    SUPERGRAPHIC.classList.remove("-scrolled-more");
  }
  if (window.scrollY >= 32 && window.innerWidth > 1024) {
    COMMERCE_HEADER.classList.add("-scrolled");
    SUPERGRAPHIC.classList.add("-scrolled");
  } else {
    COMMERCE_HEADER.classList.remove("-scrolled");
    SUPERGRAPHIC.classList.remove("-scrolled");
  }
};
window.addEventListener("scroll", handleScroll);
if (NAVIGATION_WRAPPER && PREV_LEVEL_BUTTON) {
  const observer = new MutationObserver(() => {
    NAVIGATION_LEVELS.forEach((levelClass) => {
      if (NAVIGATION_WRAPPER.classList.contains(levelClass) && levelClass !== "-level-0") {
        PREV_LEVEL_BUTTON.style.visibility = "visible";
      }
    });
    if (NAVIGATION_WRAPPER.classList.contains("-level-0")) {
      UTILITY_BAR_MOBILE?.classList.add("-visible");
    } else {
      UTILITY_BAR_MOBILE?.classList.remove("-visible");
    }
  });
  observer.observe(NAVIGATION_WRAPPER, {
    attributes: true,
    attributeFilter: ["class"]
  });
}
const mediaQuerySwitch = window.matchMedia("(max-width: 991px)");
mediaQuerySwitch.addEventListener("change", () => {
  closeMenu();
});
