export type Theme = "light" | "dark";
export const THEME_STORAGE_KEY = "ims:theme";

// Restore the saved palette before the page paints. Only known theme values are used.
export const THEME_INITIALIZER = `(function(){var theme="light";try{if(localStorage.getItem(${JSON.stringify(THEME_STORAGE_KEY)})==="dark")theme="dark";}catch(e){}document.documentElement.dataset.theme=theme;})();`;

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
}
