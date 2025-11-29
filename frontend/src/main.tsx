import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router";
import keycloak from "./keycloak.ts";

import "./index.css";

import Home from "./pages/Home.tsx";
import VideoPlayer from "./pages/VideoPlayer.tsx";
import LoginRedirect from "./pages/LoginRedirect.tsx";
import NotFound from "./pages/NotFound.tsx";

keycloak
  .init({
    onLoad: "check-sso",
    silentCheckSsoRedirectUri:
      window.location.origin + "/silent-check-sso.html",
  })
  .then(() => {
    createRoot(document.getElementById("root")!).render(
      <StrictMode>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginRedirect />} />

            <Route
              path="/"
              element={
                keycloak.authenticated ? <Home /> : <Navigate to="/login" />
              }
            />
            <Route path="/video/:id" element={<VideoPlayer />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </StrictMode>
    );
  });
