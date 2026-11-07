/**
 * Udhaar Web App Client-Side Router (webapp/src/routes/router.js)
 * Clean hash-based SPA routing with route transition lifecycle.
 */

class Router {
  constructor() {
    this.routes = new Map();
    this.currentRoute = null;
    this.currentView = null;
    this.onRouteChanged = null;

    if (typeof window !== "undefined") {
      window.addEventListener("hashchange", () => {
        this._handleRoute();
      });
    }
  }

  addRoute(path, handler) {
    this.routes.set(path, handler);
  }

  navigate(path) {
    if (typeof window !== "undefined") {
      if (window.location.hash !== path) {
        window.location.hash = path;
      } else {
        this._handleRoute();
      }
    }
  }

  getRoute() {
    if (typeof window === "undefined") return "#signin";
    const hash = window.location.hash || "#signin";
    return hash;
  }

  _handleRoute() {
    const route = this.getRoute();
    this.currentRoute = route;

    const handler = this.routes.get(route) || this.routes.get("#signin");
    if (handler) {
      if (this.currentView && typeof this.currentView.destroy === "function") {
        this.currentView.destroy();
      }
      this.currentView = handler(route);
      if (this.onRouteChanged) {
        this.onRouteChanged(route);
      }
    }
  }

  start() {
    this._handleRoute();
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { Router };
}
