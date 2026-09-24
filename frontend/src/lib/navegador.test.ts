import { describe, expect, it } from "vitest";
import { esNavegadorDeApp } from "./navegador";

describe("el navegador de dentro de una app", () => {
  it("reconoce Instagram, Facebook y TikTok, donde Google no deja entrar", () => {
    expect(esNavegadorDeApp("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) Instagram 312.0.0")).toBe(true);
    expect(esNavegadorDeApp("Mozilla/5.0 (Linux; Android 14) [FBAN/FB4A;FBAV/450.0.0]")).toBe(true);
    expect(esNavegadorDeApp("Mozilla/5.0 (Linux; Android 13) musical_ly_2023 BytedanceWebview/d8a21c6")).toBe(true);
    expect(esNavegadorDeApp("Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")).toBe(true);
  });

  it("deja en paz a Chrome y Safari de verdad", () => {
    expect(esNavegadorDeApp("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")).toBe(false);
    expect(esNavegadorDeApp("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Version/17.5 Mobile/15E148 Safari/604.1")).toBe(false);
  });
});
