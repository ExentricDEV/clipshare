import axios from "axios";
import keycloak, { freshToken } from "../keycloak";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: false,
});

// Access tokens are short-lived (5 minutes), so refresh one that is about to expire before each request.
apiClient.interceptors.request.use(async (config) => {
  if (keycloak.authenticated) {
    const token = await freshToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export default apiClient;
