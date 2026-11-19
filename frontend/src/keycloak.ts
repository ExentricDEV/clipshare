import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

/**
 * Returns an access token valid for at least 30 more seconds, refreshing it with the refresh token if needed.
 * If the session itself has expired, sends the user back to the login page.
 */
export async function freshToken(): Promise<string | undefined> {
  try {
    await keycloak.updateToken(30);
  } catch {
    await keycloak.login({ redirectUri: window.location.href });
  }
  return keycloak.token;
}

// refresh proactively too, so a tab left open never sends an expired token
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => keycloak.clearToken());
};

export default keycloak;
