import "../css/style.scss";
import m from "mithril";
import { HomePage } from "./components/pages/HomePage";
import { LoginPage } from "./components/pages/LoginPage";
import { AuthenticatedPageResolver } from "./utils/AuthenticatedPageResolver";
import { Endpoints } from "./utils/navigation/Endpoints";
import { ProfilePage } from "./components/pages/ProfilePage";

const routes: m.RouteDefs = {
  [Endpoints.LOGIN]: LoginPage,
  [Endpoints.HOME]: new AuthenticatedPageResolver(HomePage),
  [Endpoints.PROFILE]: new AuthenticatedPageResolver(ProfilePage),
};

m.route(document.getElementById("app")!, Endpoints.LOGIN, routes);
