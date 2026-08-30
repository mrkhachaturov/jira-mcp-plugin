import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app";

const root = document.getElementById("root");
if (!root) throw new Error("widget document has no #root element");

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
