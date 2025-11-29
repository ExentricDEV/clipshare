import { useEffect } from "react";
import keycloak from "../keycloak";

export default function LoginRedirect() {
  useEffect(() => {
    keycloak.login({
      redirectUri: window.location.origin + "/",
    });
  }, []);

  return null;
}
