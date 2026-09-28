// Tests de firestore.rules. Ejecutar desde esta carpeta:
//   npm install && npm test
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-zerohaus",
    firestore: { rules: readFileSync("../firestore.rules", "utf8") },
  });
});

after(async () => env?.cleanup());

// Usuario con el email ya verificado (el caso normal dentro de la app)
const verificado = (uid) =>
  env.authenticatedContext(uid, { email: `${uid}@x.es`, email_verified: true }).firestore();
// Recién registrado: sesión iniciada pero sin introducir aún el código
const sinVerificar = (uid) =>
  env.authenticatedContext(uid, { email: `${uid}@x.es`, email_verified: false }).firestore();

beforeEach(async () => {
  await env.clearFirestore();
  // Datos de partida escritos sin reglas
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "usuarios/cliente"), { uid: "cliente", tipoUsuario: "Propietario" });
    await setDoc(doc(db, "usuarios/pro"), { uid: "pro", tipoUsuario: "Técnico" });
    await setDoc(doc(db, "tecnicos/pro"), {
      uid: "pro", nombre: "Pro", rating: 0, opiniones: 0, proyectosCompletados: 0,
      tipoProfesional: "TECNICO", emailContacto: "pro@x.es",
    });
    await setDoc(doc(db, "tecnicos/legacy"), {
      uid: "", nombre: "Legacy", rating: 4, opiniones: 3, proyectosCompletados: 0,
      tipoProfesional: "TECNICO", emailContacto: "legacy@x.es",
    });
    await setDoc(doc(db, "chats/con_mensajes"), {
      participantes: ["cliente", "pro"], fechaUltimoMensaje: 1000,
    });
    await setDoc(doc(db, "chats/vacio"), {
      participantes: ["cliente", "pro"], fechaUltimoMensaje: 0,
    });
    await setDoc(doc(db, "chats/ajeno"), {
      participantes: ["otro", "pro"], fechaUltimoMensaje: 1000,
    });
  });
});

const resena = (extra = {}) => ({
  uid: "cliente", tecnicoId: "pro", puntuacion: 5, comentario: "Genial",
  nombreUsuario: "Cliente", fecha: 1, ...extra,
});

describe("reseñas", () => {
  it("se permite con un chat con mensajes", async () => {
    const db = verificado("cliente");
    await assertSucceeds(setDoc(doc(db, "resenas/cliente_pro"), resena({ chatId: "con_mensajes" })));
  });

  it("se rechaza sin chatId", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resena()));
  });

  it("se rechaza si el chat no tiene mensajes", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resena({ chatId: "vacio" })));
  });

  it("se rechaza con el chat de otra persona", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resena({ chatId: "ajeno" })));
  });

  it("se rechaza con un chat inexistente", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resena({ chatId: "no_existe" })));
  });
});

describe("reclamar perfil profesional legacy", () => {
  it("se rechaza si el email no está verificado", async () => {
    const db = env.authenticatedContext("nuevo", { email: "legacy@x.es", email_verified: false }).firestore();
    await assertFails(updateDoc(doc(db, "tecnicos/legacy"), { uid: "nuevo" }));
  });

  it("se permite con el email verificado", async () => {
    const db = env.authenticatedContext("nuevo", { email: "legacy@x.es", email_verified: true }).firestore();
    await assertSucceeds(updateDoc(doc(db, "tecnicos/legacy"), { uid: "nuevo" }));
  });

  it("no permite cambiar la valoración al reclamar", async () => {
    const db = env.authenticatedContext("nuevo", { email: "legacy@x.es", email_verified: true }).firestore();
    await assertFails(updateDoc(doc(db, "tecnicos/legacy"), { uid: "nuevo", rating: 5 }));
  });

  it("no permite descripciones gigantes", async () => {
    const db = env.authenticatedContext("nuevo", { email: "legacy@x.es", email_verified: true }).firestore();
    await assertFails(updateDoc(doc(db, "tecnicos/legacy"), { uid: "nuevo", descripcion: "x".repeat(2001) }));
  });
});

describe("eventos de estadísticas", () => {
  const hoy = () => {
    const d = new Date();
    return d.getUTCFullYear() * 10000 + (d.getUTCMonth() + 1) * 100 + d.getUTCDate();
  };

  it("un cliente registra su visita de hoy", async () => {
    const db = verificado("cliente");
    const dia = hoy();
    await assertSucceeds(setDoc(doc(db, `tecnicos/pro/eventos/cliente_${dia}_visita`), { dia, tipo: "visita" }));
  });

  it("no puede repetir la misma visita el mismo día", async () => {
    const db = verificado("cliente");
    const dia = hoy();
    await setDoc(doc(db, `tecnicos/pro/eventos/cliente_${dia}_visita`), { dia, tipo: "visita" });
    await assertFails(setDoc(doc(db, `tecnicos/pro/eventos/cliente_${dia}_visita`), { dia, tipo: "visita" }));
  });

  it("no puede inventar otro día", async () => {
    const db = verificado("cliente");
    const dia = hoy() - 1;
    await assertFails(setDoc(doc(db, `tecnicos/pro/eventos/cliente_${dia}_visita`), { dia, tipo: "visita" }));
  });

  it("no puede firmar el evento como otro usuario", async () => {
    const db = verificado("cliente");
    const dia = hoy();
    await assertFails(setDoc(doc(db, `tecnicos/pro/eventos/otro_${dia}_visita`), { dia, tipo: "visita" }));
  });

  it("el profesional no se visita a sí mismo", async () => {
    const db = verificado("pro");
    const dia = hoy();
    await assertFails(setDoc(doc(db, `tecnicos/pro/eventos/pro_${dia}_visita`), { dia, tipo: "visita" }));
  });

  it("nadie lee los eventos individuales", async () => {
    const db = verificado("pro");
    await assertFails(getDoc(doc(db, `tecnicos/pro/eventos/cliente_${hoy()}_visita`)));
  });

  it("solo el profesional lee sus estadísticas", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "estadisticas/pro"), { total: { visita: 3 } }));
    await assertSucceeds(getDoc(doc(verificado("pro"), "estadisticas/pro")));
    await assertFails(getDoc(doc(verificado("cliente"), "estadisticas/pro")));
    await assertFails(setDoc(doc(verificado("pro"), "estadisticas/pro"), { total: { visita: 999 } }));
  });
});

describe("perfil profesional y suscripciones", () => {
  it("el dueño no puede regalarse un plan", async () => {
    const db = verificado("pro");
    await assertFails(updateDoc(doc(db, "tecnicos/pro"), { planActivo: "destacado", suscripcionHasta: 9e12 }));
  });

  it("el dueño puede editar su descripción", async () => {
    const db = verificado("pro");
    await assertSucceeds(updateDoc(doc(db, "tecnicos/pro"), { descripcion: "Aislamientos" }));
  });

  it("nadie escribe /suscripciones desde el cliente", async () => {
    const db = verificado("pro");
    await assertFails(setDoc(doc(db, "suscripciones/tok"), { uid: "pro", activa: true, fechaFin: 9e12 }));
  });

  it("un usuario no lee la suscripción de otro", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "suscripciones/tok"), { uid: "pro", activa: true }));
    const db = verificado("cliente");
    await assertFails(getDoc(doc(db, "suscripciones/tok")));
  });
});

describe("email sin verificar", () => {
  it("puede crear su perfil de usuario al registrarse", async () => {
    await assertSucceeds(setDoc(doc(sinVerificar("nuevo"), "usuarios/nuevo"), {
      uid: "nuevo", nombre: "Nuevo", email: "nuevo@x.es", tipoUsuario: "Propietario",
      bloqueado: false, eliminado: false,
    }));
  });

  it("puede leer su propio perfil (comprobación de bloqueo en el login)", async () => {
    await assertSucceeds(getDoc(doc(sinVerificar("cliente"), "usuarios/cliente")));
  });

  it("puede guardar sus ajustes (token de notificaciones)", async () => {
    await assertSucceeds(setDoc(doc(sinVerificar("cliente"), "ajustes/cliente"), { tokenFCM: "t" }));
  });

  it("no ve el directorio de profesionales", async () => {
    await assertFails(getDoc(doc(sinVerificar("cliente"), "tecnicos/pro")));
  });

  it("no puede crear viviendas", async () => {
    await assertFails(setDoc(doc(sinVerificar("cliente"), "viviendas/v1"), { uid: "cliente", nombre: "Casa" }));
  });

  it("no puede abrir chats", async () => {
    await assertFails(getDoc(doc(sinVerificar("cliente"), "chats/con_mensajes")));
  });

  it("no puede leer el perfil de otro usuario", async () => {
    await assertFails(getDoc(doc(sinVerificar("cliente"), "usuarios/pro")));
  });

  it("nadie lee las huellas de los códigos", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "verificaciones_email/cliente"), { huella: "x" }));
    await assertFails(getDoc(doc(sinVerificar("cliente"), "verificaciones_email/cliente")));
    await assertFails(getDoc(doc(verificado("cliente"), "verificaciones_email/cliente")));
  });
});
