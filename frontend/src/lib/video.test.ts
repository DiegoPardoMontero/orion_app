import { describe, expect, it } from "vitest";
import { reproductorDe } from "./video";

describe("reproductorDe", () => {
  it("entiende los enlaces de compartir de YouTube y los pasa al dominio sin cookies", () => {
    const esperado = { tipo: "iframe", src: "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0&modestbranding=1" };
    expect(reproductorDe("https://youtu.be/dQw4w9WgXcQ?si=abc")).toEqual(esperado);
    expect(reproductorDe("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3")).toEqual(esperado);
    expect(reproductorDe("https://m.youtube.com/shorts/dQw4w9WgXcQ")).toEqual(esperado);
    expect(reproductorDe("https://www.youtube.com/embed/dQw4w9WgXcQ")).toEqual(esperado);
  });

  it("Vimeo, también el oculto con su llave", () => {
    expect(reproductorDe("https://vimeo.com/123456789")).toEqual({ tipo: "iframe", src: "https://player.vimeo.com/video/123456789" });
    expect(reproductorDe("https://vimeo.com/123456789/ab12cd34ef")).toEqual({
      tipo: "iframe",
      src: "https://player.vimeo.com/video/123456789?h=ab12cd34ef",
    });
  });

  it("Google Drive, con su vista previa", () => {
    expect(reproductorDe("https://drive.google.com/file/d/1AbC_dEf/view?usp=sharing")).toEqual({
      tipo: "iframe",
      src: "https://drive.google.com/file/d/1AbC_dEf/preview",
    });
  });

  it("un archivo directo se reproduce con <video>", () => {
    expect(reproductorDe("https://res.cloudinary.com/x/video/upload/bienvenida.mp4")).toEqual({
      tipo: "video",
      src: "https://res.cloudinary.com/x/video/upload/bienvenida.mp4",
    });
  });

  it("nada que no sea https, ni un YouTube sin id", () => {
    expect(reproductorDe("javascript:alert(1)")).toBeNull();
    expect(reproductorDe("http://youtu.be/dQw4w9WgXcQ")).toBeNull();
    expect(reproductorDe("https://www.youtube.com/@orionidiomas")).toBeNull();
    expect(reproductorDe("no es un enlace")).toBeNull();
  });
});
