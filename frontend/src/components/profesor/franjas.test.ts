import { describe, expect, it } from "vitest";
import {
  aHhmm,
  aMinutos,
  bajarAlPaso,
  cuposDeTramo,
  cuposPorSemana,
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

describe("cupos", () => {
  it("cuenta una clase cada media hora mientras quepa entera", () => {
    expect(cuposDeTramo(30, 55)).toBe(0);
    expect(cuposDeTramo(60, 55)).toBe(1);
    expect(cuposDeTramo(90, 55)).toBe(2);
    expect(cuposDeTramo(180, 55)).toBe(5); // 18:00–21:00 → 18, 18:30, 19, 19:30, 20
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
    // Lunes 18–20 unido son 3 (no 1 + 1); miércoles 8–11 son 5.
    expect(cuposPorSemana(semana, 55)).toBe(8);
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
