import { describe, expect, it } from "vitest";
import {
  aHhmm,
  aMinutos,
  bajarAlPaso,
  clasesDeTramo,
  clasesPorSemana,
  horasPorSemana,
  huecoLibre,
  primeraHoraLibre,
  rangoLargo,
  redondearAlPaso,
  seCruza,
  tramoArrastrado,
  unir,
  ventanaDeHoras,
} from "./franjas";

const h = (hhmm: string) => aMinutos(hhmm);

describe("horas de pared y minutos", () => {
  it("lee lo que manda el backend, con o sin segundos, y conserva los minutos", () => {
    expect(aMinutos("18:30:00")).toBe(1110);
    expect(aMinutos("05:00")).toBe(300);
    expect(aHhmm(1110)).toBe("18:30");
    expect(aHhmm(480)).toBe("08:00");
  });

  it("ajusta a la media hora", () => {
    expect(bajarAlPaso(h("18:47"))).toBe(h("18:30"));
    expect(redondearAlPaso(h("18:47"))).toBe(h("19:00"));
    expect(redondearAlPaso(h("18:44"))).toBe(h("18:30"));
  });

  it("dice el rango como se lee en Colombia", () => {
    expect(rangoLargo(h("18:00"), h("20:30"))).toBe("6:00 – 8:30 PM");
    expect(rangoLargo(h("11:00"), h("13:00"))).toBe("11:00 AM – 1:00 PM");
    expect(rangoLargo(h("05:30"), h("07:00"))).toBe("5:30 – 7:00 AM");
  });
});

describe("clases que caben", () => {
  it("cuenta las clases que caben una detrás de otra, sin pisarse", () => {
    expect(clasesDeTramo(30, 55)).toBe(0);
    expect(clasesDeTramo(60, 55)).toBe(1);
    expect(clasesDeTramo(90, 55)).toBe(1); // la segunda no alcanza: 18:00–18:55 y 19:00–19:55
    expect(clasesDeTramo(120, 55)).toBe(2);
    expect(clasesDeTramo(180, 55)).toBe(3); // 18:00–21:00 → 18, 19 y 20 (no 5 horas de inicio)
    expect(clasesDeTramo(240, 55)).toBe(4);
  });

  it("une las franjas que se tocan antes de contar, y no cuenta dos veces lo que se cruza", () => {
    expect(unir([{ inicio: h("19:00"), fin: h("20:00") }, { inicio: h("18:00"), fin: h("19:00") }])).toEqual([
      { inicio: h("18:00"), fin: h("20:00") },
    ]);
    const semana = [
      { weekday: 1, inicio: h("18:00"), fin: h("19:00") },
      { weekday: 1, inicio: h("19:00"), fin: h("20:00") },
      { weekday: 3, inicio: h("08:00"), fin: h("11:00") },
    ];
    // Lunes 18–20 unido son 2; miércoles 8–11 son 3.
    expect(clasesPorSemana(semana, 55)).toBe(5);
    expect(horasPorSemana(semana)).toBe(5);
  });
});

describe("arrastrar sobre un día", () => {
  const lunes = [
    { inicio: h("08:00"), fin: h("10:00") },
    { inicio: h("18:00"), fin: h("21:00") },
  ];

  it("encuentra el hueco libre alrededor de un minuto", () => {
    expect(huecoLibre(lunes, h("12:00"))).toEqual({ inicio: h("10:00"), fin: h("18:00") });
    expect(huecoLibre(lunes, h("09:00"))).toBeNull();
    expect(huecoLibre(lunes, h("06:00"))).toEqual({ inicio: 0, fin: h("08:00") });
    expect(huecoLibre(lunes, h("22:00"))).toEqual({ inicio: h("21:00"), fin: h("23:30") });
  });

  it("tocarse no es cruzarse", () => {
    expect(seCruza(lunes, { inicio: h("10:00"), fin: h("11:00") })).toBe(false);
    expect(seCruza(lunes, { inicio: h("09:30"), fin: h("11:00") })).toBe(true);
  });

  it("un clic abre una hora desde la media hora pulsada", () => {
    expect(tramoArrastrado(lunes, h("13:30"), h("13:30"))).toEqual({ inicio: h("13:30"), fin: h("14:30") });
  });

  it("arrastrar hacia abajo o hacia arriba da el mismo tramo, con la media hora final incluida", () => {
    expect(tramoArrastrado(lunes, h("12:00"), h("14:00"))).toEqual({ inicio: h("12:00"), fin: h("14:30") });
    expect(tramoArrastrado(lunes, h("14:00"), h("12:00"))).toEqual({ inicio: h("12:00"), fin: h("14:30") });
  });

  it("no pisa a las vecinas: se detiene donde empiezan", () => {
    expect(tramoArrastrado(lunes, h("16:00"), h("19:30"))).toEqual({ inicio: h("16:00"), fin: h("18:00") });
    expect(tramoArrastrado(lunes, h("11:00"), h("07:00"))).toEqual({ inicio: h("10:00"), fin: h("11:30") });
  });

  it("junto a una vecina estira hacia atrás para llegar a la hora", () => {
    expect(tramoArrastrado(lunes, h("17:30"), h("17:30"))).toEqual({ inicio: h("17:00"), fin: h("18:00") });
  });

  it("en un hueco de media hora abre media hora: une las dos franjas", () => {
    const conHueco = [
      { inicio: h("18:00"), fin: h("19:00") },
      { inicio: h("19:30"), fin: h("21:00") },
    ];
    expect(tramoArrastrado(conHueco, h("19:00"), h("19:00"))).toEqual({ inicio: h("19:00"), fin: h("19:30") });
  });

  it("dentro de una franja no hay nada que crear", () => {
    expect(tramoArrastrado(lunes, h("09:00"), h("12:00"))).toBeNull();
  });

  it("propone la primera hora libre desde las 8 AM", () => {
    expect(primeraHoraLibre(lunes)).toBe(h("10:00"));
    expect(primeraHoraLibre([])).toBe(h("08:00"));
  });
});

describe("la ventana de la rejilla", () => {
  it("por defecto va de 5 AM a 11 PM", () => {
    expect(ventanaDeHoras([])).toEqual({ desde: 5, hasta: 23 });
  });

  it("se ensancha para que se vea toda franja, también las de madrugada o de la media hora", () => {
    expect(ventanaDeHoras([{ inicio: h("04:30"), fin: h("06:00") }])).toEqual({ desde: 4, hasta: 23 });
    expect(ventanaDeHoras([{ inicio: h("22:00"), fin: h("23:30") }])).toEqual({ desde: 5, hasta: 24 });
  });
});
