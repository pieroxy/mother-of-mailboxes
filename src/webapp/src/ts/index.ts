import m from "mithril";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { AuthenticatedPageResolver } from "./utils/AuthenticatedPageResolver";
import { Endpoints } from "./utils/navigation/Endpoints";

const routes: m.RouteDefs = {
  [Endpoints.LOGIN]: LoginPage,
  [Endpoints.HOME]: new AuthenticatedPageResolver(HomePage),
};

m.route(document.getElementById("app")!, Endpoints.LOGIN, routes);
