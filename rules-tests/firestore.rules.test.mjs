// Tests de firestore.rules. Ejecutar desde esta carpeta:
//   npm install && npm test
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection, deleteDoc, deleteField, doc, getDoc, getDocs, query, setDoc, updateDoc, where,
} from "firebase/firestore";

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
      participantes: ["cliente", "pro"], fechaUltimoMensaje: 1000, escribieron: ["cliente", "pro"],
    });
    // El cliente escribió pero el profesional aún no ha contestado
    await setDoc(doc(db, "chats/sin_respuesta"), {
      participantes: ["cliente", "pro"], fechaUltimoMensaje: 1000, escribieron: ["cliente"],
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

  it("se rechaza si el profesional aún no ha contestado", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resena({ chatId: "sin_respuesta" })));
  });

  it("nadie se apunta como autor de un chat desde la app (lo pone el servidor)", async () => {
    const db = verificado("cliente");
    await assertFails(updateDoc(doc(db, "chats/sin_respuesta"), { escribieron: ["cliente", "pro"] }));
    await assertFails(setDoc(doc(db, "chats/nuevo"), {
      id: "nuevo", participantes: ["cliente", "pro"], nombresParticipantes: { cliente: "C", pro: "P" },
      ultimoMensaje: "", fechaUltimoMensaje: 0, noLeidosPor: { cliente: 0, pro: 0 },
      escribieron: ["cliente", "pro"],
    }));
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

// Ataques encontrados con la auditoría de reglas (firebase-security-rules-auditor)
describe("auditoría: suplantación y escalada", () => {
  const perfilNuevo = (extra = {}) => ({
    uid: "nuevo", id: "nuevo", nombre: "Nuevo", rating: 0, opiniones: 0, proyectosCompletados: 0,
    tipoProfesional: "TECNICO", emailContacto: "nuevo@x.es", planActivo: "", suscripcionHasta: 0,
    ...extra,
  });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "usuarios/nuevo"), { uid: "nuevo", tipoUsuario: "Técnico" });
      await setDoc(doc(db, "chats/con_mensajes/mensajes/m1"), { emisorUid: "cliente", texto: "hola" });
      await setDoc(doc(db, "notificaciones/n1"), { uid: "cliente", titulo: "t", detalle: "d", leida: false });
      await setDoc(doc(db, "resenas/cliente_pro"), resena({ chatId: "con_mensajes" }));
    });
  });

  it("un profesional nuevo crea su perfil sin plan, como hace la app", async () => {
    await assertSucceeds(setDoc(doc(sinVerificar("nuevo"), "tecnicos/nuevo"), perfilNuevo()));
  });

  it("un profesional nuevo no puede crear su perfil con un plan de pago", async () => {
    const db = sinVerificar("nuevo");
    await assertFails(setDoc(doc(db, "tecnicos/nuevo"),
      perfilNuevo({ planActivo: "DESTACADO", suscripcionHasta: 9e12 })));
    await assertFails(setDoc(doc(db, "tecnicos/nuevo"), perfilNuevo({ suscripcionHasta: 9e12 })));
  });

  it("quien envía un mensaje no puede atribuírselo al otro participante", async () => {
    const db = verificado("cliente");
    await assertFails(updateDoc(doc(db, "chats/con_mensajes/mensajes/m1"), { emisorUid: "pro" }));
    await assertFails(updateDoc(doc(db, "chats/con_mensajes/mensajes/m1"), { texto: "x".repeat(5000) }));
  });

  it("quien envía un mensaje puede borrarlo", async () => {
    await assertSucceeds(deleteDoc(doc(verificado("cliente"), "chats/con_mensajes/mensajes/m1")));
  });

  it("no se puede pasar una notificación propia a otro usuario", async () => {
    const db = verificado("cliente");
    await assertFails(updateDoc(doc(db, "notificaciones/n1"), { uid: "pro" }));
    await assertFails(updateDoc(doc(db, "notificaciones/n1"), { titulo: "x".repeat(5000) }));
  });

  it("se puede marcar una notificación como leída", async () => {
    await assertSucceeds(updateDoc(doc(verificado("cliente"), "notificaciones/n1"), { leida: true }));
  });

  it("una reseña se puede editar pero no añadirle campos arbitrarios", async () => {
    const db = verificado("cliente");
    await assertSucceeds(updateDoc(doc(db, "resenas/cliente_pro"), { puntuacion: 4, comentario: "Bien" }));
    await assertFails(updateDoc(doc(db, "resenas/cliente_pro"), { verificada: true }));
  });

  it("los ajustes tienen un tamaño máximo", async () => {
    const enorme = Object.fromEntries(Array.from({ length: 60 }, (_, i) => [`k${i}`, i]));
    await assertFails(setDoc(doc(verificado("cliente"), "ajustes/cliente"), enorme));
    await assertSucceeds(setDoc(doc(verificado("cliente"), "ajustes/cliente"), { idioma: "Español" }));
  });
});

// Segunda auditoría (diagnóstico 2026-10): chat y notificaciones
describe("chat: creación, cambios y mensajes", () => {
  const URL_STORAGE = "https://firebasestorage.googleapis.com/v0/b/zerohaus-2a865.firebasestorage.app/o/chats%2F";
  // Mensaje tal como lo escribe la app (MensajeChat)
  const mensaje = (extra = {}) => ({
    id: "m9", chatId: "con_mensajes", emisorUid: "cliente", emisorNombre: "Cliente",
    texto: "hola", fecha: 1, leido: false, tipo: "texto", mediaUrl: "", mediaNombre: "", mediaBytes: 0,
    ...extra,
  });
  const chatNuevo = (extra = {}) => ({
    id: "c2", participantes: ["cliente", "pro"], nombresParticipantes: { cliente: "Cliente", pro: "Pro" },
    ultimoMensaje: "", fechaUltimoMensaje: 0, noLeidosPor: { cliente: 0, pro: 0 }, ...extra,
  });

  it("se crea un chat vacío como hace la app", async () => {
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/c2"), chatNuevo()));
  });

  it("no se puede crear un chat que ya parezca tener mensajes (requisito de las reseñas)", async () => {
    await assertFails(setDoc(doc(verificado("cliente"), "chats/c2"), chatNuevo({ fechaUltimoMensaje: 1000 })));
  });

  it("no se puede crear un chat con uno mismo ni con nombres de terceros", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "chats/c2"), chatNuevo({ participantes: ["cliente", "cliente"] })));
    await assertFails(setDoc(doc(db, "chats/c2"),
      chatNuevo({ nombresParticipantes: { cliente: "Cliente", pro: "Pro", otro: "X" } })));
  });

  it("al enviar se actualizan el último mensaje, su fecha y los no leídos", async () => {
    await assertSucceeds(updateDoc(doc(verificado("cliente"), "chats/con_mensajes"),
      { ultimoMensaje: "hola", fechaUltimoMensaje: 2000, "noLeidosPor.pro": 1 }));
  });

  it("un participante no puede cambiar los nombres ni borrar el chat", async () => {
    const db = verificado("cliente");
    await assertFails(updateDoc(doc(db, "chats/con_mensajes"), { "nombresParticipantes.pro": "ZeroHaus Soporte" }));
    await assertFails(updateDoc(doc(db, "chats/con_mensajes"), { "noLeidosPor.otro": 5 }));
    await assertFails(deleteDoc(doc(db, "chats/con_mensajes")));
  });

  it("se envían texto, fotos y archivos subidos a este chat", async () => {
    const db = verificado("cliente");
    await assertSucceeds(setDoc(doc(db, "chats/con_mensajes/mensajes/m9"), mensaje()));
    await assertSucceeds(setDoc(doc(db, "chats/con_mensajes/mensajes/m10"), mensaje({
      id: "m10", tipo: "imagen", texto: "",
      mediaUrl: `${URL_STORAGE}con_mensajes%2Fm10.jpg?alt=media&token=abc`,
    })));
    await assertSucceeds(setDoc(doc(db, "chats/con_mensajes/mensajes/m11"), mensaje({
      id: "m11", tipo: "archivo", texto: "", mediaNombre: "presupuesto.pdf", mediaBytes: 1234,
      mediaUrl: `${URL_STORAGE}con_mensajes%2Fm11_presupuesto.pdf?alt=media&token=abc`,
    })));
  });

  it("un adjunto no puede apuntar fuera del Storage de este chat", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m12"),
      mensaje({ tipo: "imagen", mediaUrl: "https://ejemplo.com/rastreo.png" })));
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m12"),
      mensaje({ tipo: "archivo", mediaUrl: `${URL_STORAGE}ajeno%2Fx.pdf?alt=media` })));
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m12"),
      mensaje({ tipo: "texto", mediaUrl: `${URL_STORAGE}con_mensajes%2Fx.jpg` })));
  });

  it("los archivos del chat son solo PDF", async () => {
    await assertFails(setDoc(doc(verificado("cliente"), "chats/con_mensajes/mensajes/m14"), mensaje({
      id: "m14", tipo: "archivo", texto: "", mediaNombre: "app.apk", mediaBytes: 1234,
      mediaUrl: `${URL_STORAGE}con_mensajes%2Fm14_app.apk?alt=media&token=abc`,
    })));
  });

  it("un mensaje no lleva tipos ni campos inventados", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m13"), mensaje({ tipo: "html" })));
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m13"), mensaje({ verificado: true })));
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m13"), mensaje({ emisorNombre: "x".repeat(500) })));
  });

  it("la app no crea notificaciones: solo las Cloud Functions", async () => {
    await assertFails(setDoc(doc(verificado("cliente"), "notificaciones/n9"),
      { uid: "cliente", titulo: "t", detalle: "d", leida: false, fecha: 1, tipo: "chat" }));
  });
});

// Tercera auditoría (2026-10-14)
describe("auditoría: id del chat, bloqueo y perfil profesional", () => {
  // El id del chat se usaba dentro de la expresión que valida los adjuntos:
  // con un id como «x|.*» la alternativa aceptaba cualquier URL de internet
  const ID_MALICIOSO = "x|.*";
  const URL_EXTERNA = "https://malicioso.example/descarga%2Fapp.apk";

  beforeEach(async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `chats/${ID_MALICIOSO}`), {
        participantes: ["cliente", "pro"], fechaUltimoMensaje: 0,
      }));
  });

  it("no se puede crear un chat con un id que no sea como los de la app", async () => {
    await assertFails(setDoc(doc(verificado("cliente"), "chats/a|.*"), {
      id: "a|.*", participantes: ["cliente", "pro"], nombresParticipantes: { cliente: "C", pro: "P" },
      ultimoMensaje: "", fechaUltimoMensaje: 0, noLeidosPor: { cliente: 0, pro: 0 },
    }));
    // Los ids automáticos de Firestore (20 letras y números) siguen valiendo
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/AbCdEfGhIj0123456789"), {
      id: "AbCdEfGhIj0123456789", participantes: ["cliente", "pro"],
      nombresParticipantes: { cliente: "C", pro: "P" },
      ultimoMensaje: "", fechaUltimoMensaje: 0, noLeidosPor: { cliente: 0, pro: 0 },
    }));
  });

  it("un adjunto no puede llevar a otra web aunque el chat tenga un id raro", async () => {
    await assertFails(setDoc(doc(verificado("cliente"), `chats/${ID_MALICIOSO}/mensajes/m1`), {
      id: "m1", chatId: ID_MALICIOSO, emisorUid: "cliente", emisorNombre: "Cliente",
      texto: "", fecha: 1, leido: false, tipo: "archivo", mediaUrl: URL_EXTERNA,
      mediaNombre: "factura.pdf", mediaBytes: 1,
    }));
  });

  it("un usuario no puede borrar su perfil para quitarse un bloqueo", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      updateDoc(doc(ctx.firestore(), "usuarios/cliente"), { bloqueado: true }));
    await assertFails(deleteDoc(doc(verificado("cliente"), "usuarios/cliente")));
  });

  it("el profesional edita su perfil como hace la app", async () => {
    await assertSucceeds(updateDoc(doc(verificado("pro"), "tecnicos/pro"), {
      nombre: "Pro", ciudad: "Madrid", descripcion: "Aislamientos", telefono: "600000000",
      emailContacto: "pro@x.es", especialidades: ["Aislamiento", "Ventanas"],
      latitud: 40.4168, longitud: -3.7038,
    }));
    // Reparación de un perfil antiguo: rellena id y uid
    await assertSucceeds(updateDoc(doc(verificado("pro"), "tecnicos/pro"), { id: "pro" }));
  });

  it("el perfil profesional, que descargan todos, no admite campos inventados ni gigantes", async () => {
    const db = verificado("pro");
    await assertFails(updateDoc(doc(db, "tecnicos/pro"), { relleno: "x".repeat(100000) }));
    await assertFails(updateDoc(doc(db, "tecnicos/pro"), { ciudad: "x".repeat(5000) }));
    await assertFails(updateDoc(doc(db, "tecnicos/pro"),
      { especialidades: Array.from({ length: 50 }, (_, i) => `E${i}`) }));
    await assertFails(updateDoc(doc(db, "tecnicos/pro"), { especialidades: ["x".repeat(5000)] }));
    await assertFails(updateDoc(doc(db, "tecnicos/pro"), { latitud: 500 }));
  });
});

// Cuarta auditoría (2026-11): alta del perfil profesional y visibilidad en el directorio
describe("perfil profesional: alta con los campos del modelo y emailVerificado", () => {
  // Lo que escribe la app al registrarse (el objeto Tecnico entero, versión 2.3)
  const perfilApp = (extra = {}) => ({
    id: "nuevo", uid: "nuevo", nombre: "Nuevo", ciudad: "", rating: 0, opiniones: 0,
    proyectosCompletados: 0, distanciaKm: 0, especialidades: [], descripcion: "", telefono: "",
    emailContacto: "nuevo@x.es", latitud: 0, longitud: 0, tipoProfesional: "TECNICO",
    planActivo: "", suscripcionHasta: 0,
    ...extra,
  });

  // Perfil de una cuenta sin verificar: el servidor le puso emailVerificado a false
  const perfilOculto = (extra = {}) =>
    perfilApp({ id: "oculto", uid: "oculto", nombre: "Oculto", emailContacto: "oculto@x.es", ...extra });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      for (const u of ["nuevo", "panel", "oculto"]) {
        await setDoc(doc(db, `usuarios/${u}`), { uid: u, tipoUsuario: "Técnico" });
      }
      await setDoc(doc(db, "tecnicos/oculto"), perfilOculto({ emailVerificado: false }));
    });
  });

  it("el alta como la hace la app (objeto entero o solo lo básico) sigue permitida", async () => {
    await assertSucceeds(setDoc(doc(sinVerificar("nuevo"), "tecnicos/nuevo"), perfilApp()));
    // Panel del profesional: crea el perfil mínimo si falta
    await assertSucceeds(setDoc(doc(verificado("panel"), "tecnicos/panel"), {
      id: "panel", uid: "panel", nombre: "Panel", emailContacto: "panel@x.es",
      tipoProfesional: "TECNICO", rating: 0, opiniones: 0, proyectosCompletados: 0,
    }));
  });

  it("el alta puede llevar emailVerificado a false", async () => {
    await assertSucceeds(setDoc(doc(sinVerificar("nuevo"), "tecnicos/nuevo"), perfilApp({ emailVerificado: false })));
  });

  it("el alta no admite campos fuera del modelo", async () => {
    await assertFails(setDoc(doc(sinVerificar("nuevo"), "tecnicos/nuevo"), perfilApp({ relleno: "x" })));
    await assertFails(setDoc(doc(verificado("nuevo"), "tecnicos/nuevo"), perfilApp({ visible: true })));
  });

  it("el alta no puede llevar emailVerificado a true (solo lo pone el servidor)", async () => {
    await assertFails(setDoc(doc(sinVerificar("nuevo"), "tecnicos/nuevo"), perfilApp({ emailVerificado: true })));
    await assertFails(setDoc(doc(verificado("nuevo"), "tecnicos/nuevo"), perfilApp({ emailVerificado: true })));
    await assertFails(setDoc(doc(verificado("nuevo"), "tecnicos/nuevo"), perfilApp({ emailVerificado: "true" })));
    await assertFails(setDoc(doc(verificado("nuevo"), "tecnicos/nuevo"), perfilApp({ emailVerificado: null })));
  });

  it("al reclamar un perfil antiguo no se puede añadir emailVerificado", async () => {
    const db = env.authenticatedContext("nuevo", { email: "legacy@x.es", email_verified: true }).firestore();
    await assertFails(updateDoc(doc(db, "tecnicos/legacy"), { uid: "nuevo", emailVerificado: true }));
    await assertFails(updateDoc(doc(db, "tecnicos/legacy"), { uid: "nuevo", emailVerificado: false }));
    await assertSucceeds(updateDoc(doc(db, "tecnicos/legacy"), { uid: "nuevo" }));
  });

  it("id y distanciaKm tienen tipo y tamaño, al crear y al editar", async () => {
    await assertFails(setDoc(doc(sinVerificar("nuevo"), "tecnicos/nuevo"), perfilApp({ id: "x".repeat(129) })));
    await assertFails(setDoc(doc(sinVerificar("nuevo"), "tecnicos/nuevo"), perfilApp({ distanciaKm: "x".repeat(5000) })));
    await assertFails(updateDoc(doc(verificado("pro"), "tecnicos/pro"), { id: "x".repeat(5000) }));
    await assertFails(updateDoc(doc(verificado("pro"), "tecnicos/pro"), { distanciaKm: "lejos" }));
    await assertSucceeds(updateDoc(doc(verificado("pro"), "tecnicos/pro"), { distanciaKm: 12.5 }));
  });

  it("el dueño no puede marcarse como verificado ni quitar el campo", async () => {
    const db = verificado("oculto");
    await assertFails(updateDoc(doc(db, "tecnicos/oculto"), { emailVerificado: true }));
    await assertFails(updateDoc(doc(db, "tecnicos/oculto"), { emailVerificado: deleteField() }));
    // Sobrescribir el documento entero sin el campo también lo quitaría
    await assertFails(setDoc(doc(db, "tecnicos/oculto"), perfilOculto()));
    await assertFails(updateDoc(doc(verificado("pro"), "tecnicos/pro"), { emailVerificado: true }));
    // Reescribirlo entero con el mismo valor (reparación del panel) sí se permite
    await assertSucceeds(setDoc(doc(db, "tecnicos/oculto"), perfilOculto({ emailVerificado: false })));
    // Y fusionando (lo que hace la app al guardar el perfil entero) el campo se conserva
    await assertSucceeds(setDoc(doc(db, "tecnicos/oculto"), perfilOculto({ descripcion: "Nueva" }), { merge: true }));
  });

  it("con emailVerificado a false el dueño sigue editando su perfil como hace la app", async () => {
    await assertSucceeds(updateDoc(doc(verificado("oculto"), "tecnicos/oculto"), {
      nombre: "Oculto", ciudad: "Madrid", descripcion: "Aislamientos", telefono: "600000000",
      emailContacto: "oculto@x.es", especialidades: ["Aislamiento"], latitud: 40.4168, longitud: -3.7038,
    }));
  });
});

const MINUTO = 60 * 1000;
const DIA = 24 * 60 * MINUTO;

// Quinta auditoría (2026-11): campos de las reseñas y autovaloración
describe("reseñas: campos del modelo, nombre, fecha y autovaloración", () => {
  // Lo que escribe la app (el objeto Resena entero, versión 2.3)
  const resenaApp = (extra = {}) => ({
    id: "cliente_pro", tecnicoId: "pro", uid: "cliente", nombreUsuario: "Cliente",
    puntuacion: 5, comentario: "Genial", chatId: "con_mensajes", fecha: Date.now(), ...extra,
  });

  it("la app publica y vuelve a valorar con el objeto entero", async () => {
    const db = verificado("cliente");
    await assertSucceeds(setDoc(doc(db, "resenas/cliente_pro"), resenaApp()));
    // Volver a valorar sobrescribe la reseña con la fecha nueva
    await assertSucceeds(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ puntuacion: 3, comentario: "Regular" })));
    // Un móvil con la hora adelantada sigue pudiendo valorar (justo por debajo del margen de 24 h)
    await assertSucceeds(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ fecha: Date.now() + MINUTO })));
    await assertSucceeds(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ fecha: Date.now() + DIA - MINUTO })));
  });

  it("no admite campos fuera del modelo ni otro id", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ verificada: true })));
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ relleno: "x".repeat(10000) })));
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ id: "otro_pro" })));
  });

  it("el nombre del autor es un texto de hasta 100 caracteres", async () => {
    const db = verificado("cliente");
    // El administrador puede poner nombres de más de 60: esos usuarios también valoran
    await assertSucceeds(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ nombreUsuario: "x".repeat(100) })));
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ nombreUsuario: "x".repeat(101) })));
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ nombreUsuario: 7 })));
    const { nombreUsuario, ...sinNombre } = resenaApp();
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), sinNombre));
  });

  it("la fecha es un número y no puede estar en el futuro", async () => {
    const db = verificado("cliente");
    // Margen de 24 h para móviles con la hora adelantada: justo por encima, no
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ fecha: Date.now() + DIA + MINUTO })));
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ fecha: "9999999999999" })));
    const { fecha, ...sinFecha } = resenaApp();
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), sinFecha));
  });

  it("al editar se aplican los mismos límites", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "resenas/cliente_pro"), resenaApp()));
    const db = verificado("cliente");
    await assertFails(updateDoc(doc(db, "resenas/cliente_pro"), { nombreUsuario: "x".repeat(101) }));
    await assertFails(updateDoc(doc(db, "resenas/cliente_pro"), { fecha: Date.now() + DIA + MINUTO }));
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ fecha: Date.now() + DIA + MINUTO })));
    await assertFails(setDoc(doc(db, "resenas/cliente_pro"), resenaApp({ verificada: true })));
    await assertSucceeds(updateDoc(doc(db, "resenas/cliente_pro"), { puntuacion: 4, fecha: Date.now() }));
  });

  it("el dueño de un perfil antiguo (id distinto del uid) no puede valorarse a sí mismo", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "tecnicos/antiguo"), {
        uid: "cliente", nombre: "Antiguo", rating: 0, opiniones: 0, proyectosCompletados: 0,
        tipoProfesional: "TECNICO", emailContacto: "cliente@x.es",
      }));
    await assertFails(setDoc(doc(verificado("cliente"), "resenas/cliente_antiguo"),
      resenaApp({ id: "cliente_antiguo", tecnicoId: "antiguo" })));
  });
});

// Quinta auditoría (2026-11): chat nuevo, fecha de los mensajes y /usuarios
describe("chat nuevo y fecha de los mensajes", () => {
  // Lo que escribe la app al abrir una conversación (el objeto Chat entero)
  const chatApp = (extra = {}) => ({
    id: "c3", participantes: ["cliente", "pro"], nombresParticipantes: { cliente: "Cliente", pro: "Pro" },
    ultimoMensaje: "", fechaUltimoMensaje: 0, noLeidosPor: { cliente: 0, pro: 0 }, escribieron: [],
    ...extra,
  });
  // Mensaje de texto tal como lo escribe la app (MensajeChat)
  const mensajeApp = (extra = {}) => ({
    id: "m20", chatId: "con_mensajes", emisorUid: "cliente", emisorNombre: "Cliente",
    texto: "hola", fecha: Date.now(), leido: false, tipo: "texto", mediaUrl: "", mediaNombre: "", mediaBytes: 0,
    ...extra,
  });

  it("se crea un chat como lo escribe la app", async () => {
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/c3"), chatApp()));
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/c4"),
      chatApp({ id: "c4", nombresParticipantes: { cliente: "x".repeat(100), pro: "Pro" } })));
  });

  it("un chat nuevo no lleva campos fuera del modelo ni otro id", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "chats/c3"), chatApp({ relleno: "x".repeat(10000) })));
    await assertFails(setDoc(doc(db, "chats/c3"), chatApp({ id: "otro" })));
    await assertFails(setDoc(doc(db, "chats/c3"), chatApp({ participantes: ["cliente", "x".repeat(5000)] })));
  });

  it("los nombres de los participantes son textos de hasta 100 caracteres", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "chats/c3"),
      chatApp({ nombresParticipantes: { cliente: "Cliente", pro: "x".repeat(101) } })));
    await assertFails(setDoc(doc(db, "chats/c3"),
      chatApp({ nombresParticipantes: { cliente: { falso: true }, pro: "Pro" } })));
  });

  it("los no leídos de un chat nuevo empiezan a cero", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "chats/c3"), chatApp({ noLeidosPor: { cliente: 0, pro: "x".repeat(5000) } })));
    await assertFails(setDoc(doc(db, "chats/c3"), chatApp({ noLeidosPor: { cliente: 0, pro: 99 } })));
    await assertFails(setDoc(doc(db, "chats/c3"), chatApp({ escribieron: "" })));
  });

  it("la fecha del mensaje es un número que no está en el futuro", async () => {
    const db = verificado("cliente");
    // Justo por encima del margen de 24 h
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m20"), mensajeApp({ fecha: Date.now() + DIA + MINUTO })));
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m20"), mensajeApp({ fecha: "mañana" })));
    const { fecha, ...sinFecha } = mensajeApp();
    await assertFails(setDoc(doc(db, "chats/con_mensajes/mensajes/m20"), sinFecha));
  });

  it("los mensajes escritos sin conexión llegan tarde y se aceptan", async () => {
    const db = verificado("cliente");
    await assertSucceeds(setDoc(doc(db, "chats/con_mensajes/mensajes/m21"),
      mensajeApp({ id: "m21", fecha: Date.now() - 7 * DIA })));
    // Móvil con la hora adelantada, justo por debajo del margen de 24 h
    await assertSucceeds(setDoc(doc(db, "chats/con_mensajes/mensajes/m22"),
      mensajeApp({ id: "m22", fecha: Date.now() + MINUTO })));
    await assertSucceeds(setDoc(doc(db, "chats/con_mensajes/mensajes/m23"),
      mensajeApp({ id: "m23", fecha: Date.now() + DIA - MINUTO })));
  });
});

describe("/usuarios: email de la cuenta y topes", () => {
  const sesion = (uid, email, emailVerificado = false) =>
    env.authenticatedContext(uid, { email, email_verified: emailVerificado }).firestore();
  // Lo que escribe la app al registrarse (el objeto Usuario entero)
  const usuarioApp = (extra = {}) => ({
    uid: "nuevo", nombre: "Nuevo", email: "nuevo@x.es", tipoUsuario: "Propietario", fotoPerfil: "",
    fechaRegistro: Date.now(), bloqueado: false, eliminado: false, ...extra,
  });
  const URL_FOTO = "https://firebasestorage.googleapis.com/v0/b/zerohaus-2a865.firebasestorage.app/o/"
    + "perfiles%2Fnuevo%2Ffoto_perfil?alt=media&token=0b9f3c1e-1234-4abc-9def-0123456789ab";

  it("el registro como lo hace la app sigue permitido, aunque el email lleve mayúsculas", async () => {
    await assertSucceeds(setDoc(doc(sesion("nuevo", "nuevo@x.es"), "usuarios/nuevo"), usuarioApp()));
    await env.clearFirestore();
    await assertSucceeds(setDoc(doc(sesion("nuevo", "nuevo@x.es"), "usuarios/nuevo"),
      usuarioApp({ email: "Nuevo@X.es" })));
  });

  it("el email del perfil tiene que ser el de la cuenta", async () => {
    await assertFails(setDoc(doc(sesion("nuevo", "nuevo@x.es"), "usuarios/nuevo"),
      usuarioApp({ email: "otra.persona@x.es" })));
  });

  it("el administrador crea el perfil de otra cuenta con su email (panel de administración)", async () => {
    const admin = env.authenticatedContext("admin", { email: "admin@x.es", email_verified: true, admin: true }).firestore();
    await assertSucceeds(setDoc(doc(admin, "usuarios/creado"),
      usuarioApp({ uid: "creado", nombre: "Creado por el admin", email: "creado@x.es" })));
  });

  it("al registrarse no se admiten campos fuera del modelo ni gigantes", async () => {
    const db = sesion("nuevo", "nuevo@x.es");
    await assertFails(setDoc(doc(db, "usuarios/nuevo"), usuarioApp({ relleno: "x".repeat(10000) })));
    await assertFails(setDoc(doc(db, "usuarios/nuevo"), usuarioApp({ fotoPerfil: "x".repeat(5000) })));
    await assertFails(setDoc(doc(db, "usuarios/nuevo"), usuarioApp({ fechaRegistro: "x".repeat(5000) })));
  });

  it("al editar el perfil solo cambian el nombre y la foto, con tope", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "usuarios/nuevo"), usuarioApp()));
    const db = sesion("nuevo", "nuevo@x.es", true);
    // Lo que hace la app al guardar el perfil o subir la foto
    await assertSucceeds(setDoc(doc(db, "usuarios/nuevo"), { nombre: "Nuevo Nombre", fotoPerfil: URL_FOTO }, { merge: true }));
    await assertFails(updateDoc(doc(db, "usuarios/nuevo"), { relleno: "x".repeat(10000) }));
    await assertFails(updateDoc(doc(db, "usuarios/nuevo"), { fotoPerfil: "x".repeat(5000) }));
  });
});

// Bloque D (T-D1): bloquear y denunciar usuarios
const comoAdmin = () =>
  env.authenticatedContext("admin", { email: "admin@x.es", email_verified: true, admin: true }).firestore();

describe("bloqueos entre usuarios", () => {
  // Lo que escribe la app al bloquear (/bloqueos/{autor}_{bloqueado})
  const bloqueo = (extra = {}) => ({ autor: "cliente", bloqueado: "pro", fecha: Date.now(), ...extra });
  const sembrar = (id, datos) =>
    env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), `bloqueos/${id}`), datos));

  it("un usuario bloquea a otro como hace la app", async () => {
    await assertSucceeds(setDoc(doc(verificado("cliente"), "bloqueos/cliente_pro"), bloqueo()));
  });

  it("no se bloquea en nombre de otro ni con un id que no sea autor_bloqueado", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "bloqueos/otro_pro"), bloqueo({ autor: "otro" })));
    await assertFails(setDoc(doc(db, "bloqueos/pro_cliente"), bloqueo()));
    await assertFails(setDoc(doc(db, "bloqueos/cliente_otro"), bloqueo()));
    await assertFails(setDoc(doc(db, "bloqueos/x"), bloqueo()));
  });

  it("no se bloquea uno a sí mismo", async () => {
    await assertFails(setDoc(doc(verificado("cliente"), "bloqueos/cliente_cliente"), bloqueo({ bloqueado: "cliente" })));
  });

  it("sin cuenta verificada no se bloquea", async () => {
    await assertFails(setDoc(doc(sinVerificar("cliente"), "bloqueos/cliente_pro"), bloqueo()));
  });

  it("solo los campos del modelo, con tipo y tamaño", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "bloqueos/cliente_pro"), bloqueo({ motivo: "x" })));
    await assertFails(setDoc(doc(db, "bloqueos/cliente_pro"), bloqueo({ fecha: Date.now() + 2 * DIA })));
    await assertFails(setDoc(doc(db, "bloqueos/cliente_pro"), bloqueo({ fecha: "hoy" })));
    const { fecha, ...sinFecha } = bloqueo();
    await assertFails(setDoc(doc(db, "bloqueos/cliente_pro"), sinFecha));
    const largo = "x".repeat(129);
    await assertFails(setDoc(doc(db, `bloqueos/cliente_${largo}`), bloqueo({ bloqueado: largo })));
    await assertFails(setDoc(doc(db, "bloqueos/cliente_"), bloqueo({ bloqueado: "" })));
  });

  it("el autor lee sus bloqueos (uno a uno y con la consulta de la app)", async () => {
    await sembrar("cliente_pro", bloqueo());
    const db = verificado("cliente");
    await assertSucceeds(getDoc(doc(db, "bloqueos/cliente_pro")));
    await assertSucceeds(getDocs(query(collection(db, "bloqueos"), where("autor", "==", "cliente"))));
    // Saber si ya ha bloqueado a alguien: leer su propio id aunque no exista
    await assertSucceeds(getDoc(doc(db, "bloqueos/cliente_otro")));
  });

  it("el bloqueado no sabe que le han bloqueado y nadie más lo lee", async () => {
    await sembrar("cliente_pro", bloqueo());
    await assertFails(getDoc(doc(verificado("pro"), "bloqueos/cliente_pro")));
    await assertFails(getDocs(query(collection(verificado("pro"), "bloqueos"), where("bloqueado", "==", "pro"))));
    await assertFails(getDoc(doc(verificado("otro"), "bloqueos/cliente_pro")));
    await assertFails(getDocs(query(collection(verificado("otro"), "bloqueos"), where("autor", "==", "cliente"))));
    // Ni siquiera si existe o no un bloqueo ajeno
    await assertFails(getDoc(doc(verificado("pro"), "bloqueos/otro_pro")));
  });

  it("el bloqueado no puede listar quién le ha bloqueado, ni afinando la consulta", async () => {
    await sembrar("cliente_pro", bloqueo());
    const db = verificado("pro");
    await assertFails(getDocs(query(collection(db, "bloqueos"), where("bloqueado", "==", "pro"))));
    await assertFails(getDocs(query(collection(db, "bloqueos"),
      where("bloqueado", "==", "pro"), where("autor", "==", "cliente"))));
    await assertFails(getDocs(collection(db, "bloqueos")));
  });

  it("el autor desbloquea; el bloqueado no puede quitárselo ni nadie cambiarlo", async () => {
    await sembrar("cliente_pro", bloqueo());
    await assertFails(deleteDoc(doc(verificado("pro"), "bloqueos/cliente_pro")));
    await assertFails(updateDoc(doc(verificado("cliente"), "bloqueos/cliente_pro"), { fecha: 1 }));
    await assertSucceeds(deleteDoc(doc(verificado("cliente"), "bloqueos/cliente_pro")));
  });

  it("el administrador lee, cambia y borra bloqueos", async () => {
    await sembrar("cliente_pro", bloqueo());
    const admin = comoAdmin();
    await assertSucceeds(getDoc(doc(admin, "bloqueos/cliente_pro")));
    await assertSucceeds(updateDoc(doc(admin, "bloqueos/cliente_pro"), { fecha: 1 }));
    await assertSucceeds(deleteDoc(doc(admin, "bloqueos/cliente_pro")));
  });
});

describe("un bloqueo corta el chat en los dos sentidos", () => {
  const mensajeDe = (emisor, extra = {}) => ({
    id: "mb", chatId: "con_mensajes", emisorUid: emisor, emisorNombre: emisor, texto: "hola",
    fecha: Date.now(), leido: false, tipo: "texto", mediaUrl: "", mediaNombre: "", mediaBytes: 0, ...extra,
  });
  const chatNuevo = (id) => ({
    id, participantes: ["cliente", "pro"], nombresParticipantes: { cliente: "Cliente", pro: "Pro" },
    ultimoMensaje: "", fechaUltimoMensaje: 0, noLeidosPor: { cliente: 0, pro: 0 }, escribieron: [],
  });
  const bloquear = (autor, bloqueado) =>
    env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `bloqueos/${autor}_${bloqueado}`), { autor, bloqueado, fecha: 1 }));

  it("sin bloqueo los dos escriben y abren chats", async () => {
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/con_mensajes/mensajes/mb"), mensajeDe("cliente")));
    await assertSucceeds(setDoc(doc(verificado("pro"), "chats/con_mensajes/mensajes/mc"), mensajeDe("pro", { id: "mc" })));
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/nuevoA"), chatNuevo("nuevoA")));
  });

  for (const [autor, bloqueado] of [["cliente", "pro"], ["pro", "cliente"]]) {
    it(`con ${autor} bloqueando a ${bloqueado} ninguno escribe al otro`, async () => {
      await bloquear(autor, bloqueado);
      await assertFails(setDoc(doc(verificado("cliente"), "chats/con_mensajes/mensajes/mb"), mensajeDe("cliente")));
      await assertFails(setDoc(doc(verificado("pro"), "chats/con_mensajes/mensajes/mc"), mensajeDe("pro", { id: "mc" })));
    });

    it(`con ${autor} bloqueando a ${bloqueado} ninguno abre un chat nuevo`, async () => {
      await bloquear(autor, bloqueado);
      await assertFails(setDoc(doc(verificado("cliente"), "chats/nuevoA"), chatNuevo("nuevoA")));
      await assertFails(setDoc(doc(verificado("pro"), "chats/nuevoB"), chatNuevo("nuevoB")));
    });

    it(`con ${autor} bloqueando a ${bloqueado} no se cuela texto por el último mensaje del chat`, async () => {
      await bloquear(autor, bloqueado);
      for (const quien of ["cliente", "pro"]) {
        const otro = quien === "cliente" ? "pro" : "cliente";
        await assertFails(updateDoc(doc(verificado(quien), "chats/con_mensajes"),
          { ultimoMensaje: "sigo aquí", fechaUltimoMensaje: Date.now() }));
        await assertFails(updateDoc(doc(verificado(quien), "chats/con_mensajes"), { [`noLeidosPor.${otro}`]: 7 }));
        // Marcar como leído lo propio sigue funcionando (la app lo hace al abrir el chat)
        await assertSucceeds(updateDoc(doc(verificado(quien), "chats/con_mensajes"), { [`noLeidosPor.${quien}`]: 0 }));
      }
    });
  }

  it("al desbloquear el chat vuelve a funcionar como siempre", async () => {
    await bloquear("cliente", "pro");
    await assertFails(setDoc(doc(verificado("pro"), "chats/con_mensajes/mensajes/mc"), mensajeDe("pro", { id: "mc" })));
    // El autor borra el bloqueo desde la app
    await assertSucceeds(deleteDoc(doc(verificado("cliente"), "bloqueos/cliente_pro")));
    await assertSucceeds(setDoc(doc(verificado("pro"), "chats/con_mensajes/mensajes/mc"), mensajeDe("pro", { id: "mc" })));
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/con_mensajes/mensajes/mb"), mensajeDe("cliente")));
    await assertSucceeds(updateDoc(doc(verificado("pro"), "chats/con_mensajes"),
      { ultimoMensaje: "hola", fechaUltimoMensaje: Date.now(), "noLeidosPor.cliente": 1 }));
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/nuevoA"), chatNuevo("nuevoA")));
  });

  it("un bloqueo entre otros dos no afecta a este chat", async () => {
    await bloquear("cliente", "otro");
    await bloquear("otro", "pro");
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/con_mensajes/mensajes/mb"), mensajeDe("cliente")));
    await assertSucceeds(setDoc(doc(verificado("cliente"), "chats/nuevoA"), chatNuevo("nuevoA")));
    await assertSucceeds(updateDoc(doc(verificado("cliente"), "chats/con_mensajes"),
      { ultimoMensaje: "hola", fechaUltimoMensaje: Date.now(), "noLeidosPor.pro": 1 }));
  });

  it("las reseñas no se bloquean: el profesional no puede silenciar una legítima", async () => {
    await bloquear("pro", "cliente");
    await assertSucceeds(setDoc(doc(verificado("cliente"), "resenas/cliente_pro"), resena({ chatId: "con_mensajes" })));
  });

  it("el administrador sigue pudiendo escribir en el chat", async () => {
    await bloquear("cliente", "pro");
    await assertSucceeds(setDoc(doc(comoAdmin(), "chats/con_mensajes/mensajes/md"), mensajeDe("admin", { id: "md" })));
  });
});

describe("denuncias", () => {
  // Lo que escribe la app al denunciar (/denuncias/{autor}_{objetivo})
  const denuncia = (extra = {}) => ({
    autor: "cliente", objetivo: "pro", contexto: "chat", referencia: "con_mensajes",
    motivo: "acoso", comentario: "Me insulta", fecha: Date.now(), ...extra,
  });
  const sembrar = (id, datos) =>
    env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), `denuncias/${id}`), datos));

  it("se denuncia como hace la app, con o sin comentario", async () => {
    await assertSucceeds(setDoc(doc(verificado("cliente"), "denuncias/cliente_pro"), denuncia()));
    const { comentario, ...sinComentario } = denuncia({ autor: "pro", objetivo: "cliente", contexto: "resena" });
    await assertSucceeds(setDoc(doc(verificado("pro"), "denuncias/pro_cliente"), sinComentario));
  });

  it("los tres contextos y todos los motivos de la lista valen", async () => {
    let i = 0;
    for (const contexto of ["chat", "perfil", "resena"]) {
      for (const motivo of ["spam", "acoso", "fraude", "inapropiado", "suplantacion", "otro"]) {
        await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), "denuncias/cliente_pro")));
        await assertSucceeds(setDoc(doc(verificado("cliente"), "denuncias/cliente_pro"),
          denuncia({ contexto, motivo, referencia: `ref${i++}` })), `${contexto}/${motivo}`);
      }
    }
  });

  it("solo una por autor y objetivo: no se puede volver a enviar ni editar", async () => {
    const db = verificado("cliente");
    await assertSucceeds(setDoc(doc(db, "denuncias/cliente_pro"), denuncia()));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ motivo: "spam" })));
    await assertFails(updateDoc(doc(db, "denuncias/cliente_pro"), { comentario: "otra cosa" }));
    await assertFails(deleteDoc(doc(db, "denuncias/cliente_pro")));
  });

  it("no se denuncia uno a sí mismo ni a quien no existe", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "denuncias/cliente_cliente"), denuncia({ objetivo: "cliente" })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_fantasma"), denuncia({ objetivo: "fantasma" })));
  });

  it("no se denuncia en nombre de otro ni con otro id", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "denuncias/otro_pro"), denuncia({ autor: "otro" })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_otro"), denuncia()));
    await assertFails(setDoc(doc(db, "denuncias/pro_cliente"), denuncia()));
    await assertFails(setDoc(doc(sinVerificar("cliente"), "denuncias/cliente_pro"), denuncia()));
  });

  it("motivo y contexto solo de la lista", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ motivo: "me cae mal" })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ contexto: "foro" })));
    const { motivo, ...sinMotivo } = denuncia();
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), sinMotivo));
  });

  it("el estado y la evidencia solo los pone el servidor o el administrador", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ estado: "revisada" })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ estado: "pendiente" })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ evidencia: { mensajes: [] } })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ relleno: "x" })));
  });

  it("referencia, comentario y fecha con tipo y tope", async () => {
    const db = verificado("cliente");
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ referencia: "" })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ referencia: "x".repeat(301) })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ referencia: 7 })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ comentario: "x".repeat(1001) })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ comentario: 7 })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ fecha: Date.now() + 2 * DIA })));
    await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ fecha: "hoy" })));
    await assertSucceeds(setDoc(doc(db, "denuncias/cliente_pro"),
      denuncia({ referencia: "x".repeat(300), comentario: "x".repeat(1000) })));
  });

  it("la referencia es un id como los de la app: sin saltos de línea, enlaces ni rutas", async () => {
    const db = verificado("cliente");
    for (const referencia of ["con\nsalto", "https://malicioso.example/x", "con espacios",
                              "con_mensajes/mensajes/m1", "ñandú", "a<b>", "x".repeat(301)]) {
      await assertFails(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ referencia })), JSON.stringify(referencia));
    }
    // Id automático de un chat, uid de un perfil e id `{uid}_{tecnicoId}` de una reseña
    for (const referencia of ["AbCdEfGhIj0123456789", "aB3dE6gH9jK2mN5pQ8sT1vW4yZ7b",
                              "uidCliente_uidPro", "perfil-antiguo_1"]) {
      await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), "denuncias/cliente_pro")));
      await assertSucceeds(setDoc(doc(db, "denuncias/cliente_pro"), denuncia({ referencia })), referencia);
    }
  });

  it("el autor sabe si ya denunció; el denunciado y los demás no la leen", async () => {
    await sembrar("cliente_pro", { ...denuncia(), evidencia: { mensajes: [] }, estado: "pendiente" });
    const db = verificado("cliente");
    await assertSucceeds(getDoc(doc(db, "denuncias/cliente_pro")));
    await assertSucceeds(getDoc(doc(db, "denuncias/cliente_otro")));   // aún no la ha hecho
    await assertSucceeds(getDocs(query(collection(db, "denuncias"), where("autor", "==", "cliente"))));
    await assertFails(getDoc(doc(verificado("pro"), "denuncias/cliente_pro")));
    await assertFails(getDocs(query(collection(verificado("pro"), "denuncias"), where("objetivo", "==", "pro"))));
    await assertFails(getDoc(doc(verificado("otro"), "denuncias/cliente_pro")));
    await assertFails(getDocs(query(collection(verificado("otro"), "denuncias"), where("autor", "==", "cliente"))));
  });

  it("el administrador las lee, las marca como revisadas y las borra", async () => {
    await sembrar("cliente_pro", { ...denuncia(), estado: "pendiente" });
    const admin = comoAdmin();
    await assertSucceeds(getDocs(collection(admin, "denuncias")));
    await assertSucceeds(updateDoc(doc(admin, "denuncias/cliente_pro"), { estado: "revisada" }));
    await assertSucceeds(deleteDoc(doc(admin, "denuncias/cliente_pro")));
  });
});

// Bloque E (T-E6): pagos con retención. Todo lo escribe el servidor (Admin SDK)
describe("pagos: solo los leen las partes y el administrador; nadie los escribe desde la app", () => {
  // Lo que escribe la Cloud Function al solicitar y cobrar un pago
  const pago = (extra = {}) => ({
    id: "p1", cliente: "cliente", profesional: "pro", chatId: "con_mensajes",
    descripcion: "Instalar aerotermia", importeCent: 10000, comisionCent: 1000, netoCent: 9000,
    moneda: "eur", comisionPct: 10, liberacionDias: 7, estado: "retenido",
    historial: [{ estado: "solicitado", fecha: 1 }, { estado: "retenido", fecha: 2 }],
    creado: 1, actualizado: 2, stripePaymentIntentId: "pi_test", stripeTransferId: "",
    stripeRefundId: "", terminadoEn: 0, liberarEn: 0, disputa: null, ...extra,
  });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "pagos/p1"), pago());
      // Otro cliente con el mismo profesional: el primer cliente no debe verlo
      await setDoc(doc(db, "pagos/p2"), pago({ id: "p2", cliente: "otro", chatId: "ajeno" }));
    });
  });

  it("el cliente y el profesional leen su pago", async () => {
    await assertSucceeds(getDoc(doc(verificado("cliente"), "pagos/p1")));
    await assertSucceeds(getDoc(doc(verificado("pro"), "pagos/p1")));
  });

  it("un tercero no lee el pago ni lo consulta con el uid de otro", async () => {
    const db = verificado("otro");
    await assertFails(getDoc(doc(db, "pagos/p1")));
    await assertFails(getDocs(query(collection(db, "pagos"), where("cliente", "==", "cliente"))));
    await assertFails(getDocs(query(collection(db, "pagos"), where("profesional", "==", "pro"))));
  });

  it("sin el email verificado ni las partes lo leen", async () => {
    await assertFails(getDoc(doc(sinVerificar("cliente"), "pagos/p1")));
    await assertFails(getDoc(doc(sinVerificar("pro"), "pagos/p1")));
    await assertFails(getDocs(query(collection(sinVerificar("cliente"), "pagos"),
      where("cliente", "==", "cliente"))));
  });

  it("las consultas de la app: por cliente, por profesional y por chat con una de las partes", async () => {
    const cli = verificado("cliente");
    const pro = verificado("pro");
    const comoCliente = await assertSucceeds(getDocs(query(collection(cli, "pagos"),
      where("cliente", "==", "cliente"))));
    assert.equal(comoCliente.size, 1);
    const comoPro = await assertSucceeds(getDocs(query(collection(pro, "pagos"),
      where("profesional", "==", "pro"))));
    assert.equal(comoPro.size, 2);
    const chatCliente = await assertSucceeds(getDocs(query(collection(cli, "pagos"),
      where("chatId", "==", "con_mensajes"), where("cliente", "==", "cliente"))));
    assert.equal(chatCliente.size, 1);
    const chatPro = await assertSucceeds(getDocs(query(collection(pro, "pagos"),
      where("chatId", "==", "ajeno"), where("profesional", "==", "pro"))));
    assert.equal(chatPro.size, 1);
  });

  it("una consulta que no filtra por la propia parte se deniega", async () => {
    const db = verificado("cliente");
    await assertFails(getDocs(collection(db, "pagos")));
    await assertFails(getDocs(query(collection(db, "pagos"), where("chatId", "==", "con_mensajes"))));
    await assertFails(getDocs(query(collection(db, "pagos"), where("cliente", "==", "otro"))));
    await assertFails(getDocs(query(collection(db, "pagos"), where("estado", "==", "retenido"))));
  });

  it("el administrador lee cualquier pago y los lista todos", async () => {
    const admin = comoAdmin();
    await assertSucceeds(getDoc(doc(admin, "pagos/p2")));
    const todos = await assertSucceeds(getDocs(collection(admin, "pagos")));
    assert.equal(todos.size, 2);
  });

  it("nadie crea, cambia ni borra pagos desde la app, tampoco el administrador", async () => {
    for (const [quien, db] of [["cliente", verificado("cliente")], ["pro", verificado("pro")],
                               ["admin", comoAdmin()]]) {
      await assertFails(setDoc(doc(db, "pagos/nuevo"), pago({ id: "nuevo" })), `${quien}: crear`);
      await assertFails(updateDoc(doc(db, "pagos/p1"), { estado: "liberado" }), `${quien}: estado`);
      await assertFails(updateDoc(doc(db, "pagos/p1"), { importeCent: 1 }), `${quien}: importe`);
      await assertFails(setDoc(doc(db, "pagos/p1"), pago({ netoCent: 10000, comisionCent: 0 })),
        `${quien}: reescribir`);
      await assertFails(deleteDoc(doc(db, "pagos/p1")), `${quien}: borrar`);
    }
  });
});

describe("cuentas de Stripe Connect y datos internos de los pagos", () => {
  const cuenta = (extra = {}) => ({
    accountId: "acct_test", estado: "pendiente", chargesEnabled: false, payoutsEnabled: false,
    detailsSubmitted: false, actualizado: 1, ...extra,
  });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "cuentas_stripe/pro"), cuenta());
      await setDoc(doc(db, "eventos_stripe/evt_1"), { tipo: "payment_intent.succeeded", procesado: 1 });
      await setDoc(doc(db, "limites_pagos/cliente"), { solicitudes: 3, dia: 20261118 });
      await setDoc(doc(db, "operaciones_pagos/p1"), {
        pagoId: "p1", operacion: "transferencia", evento: "confirmar", reserva: "r1", creado: 1, renovado: 1, intento: 1,
      });
      await setDoc(doc(db, "avisos_admin_pagos/p1_reembolso_fallido_re_1"), { asunto: "x", texto: "y", fecha: 1 });
      await setDoc(doc(db, "limpieza_stripe/l1"), { cobros: ["pi_1"], customerId: "cus_1", fallos: 0, proximoIntento: 0 });
    });
  });

  it("cada uno lee su propia cuenta de Stripe y el administrador todas", async () => {
    await assertSucceeds(getDoc(doc(verificado("pro"), "cuentas_stripe/pro")));
    await assertSucceeds(getDoc(doc(comoAdmin(), "cuentas_stripe/pro")));
  });

  it("nadie lee ni lista la cuenta de Stripe de otro; sin verificar, tampoco la propia", async () => {
    await assertFails(getDoc(doc(verificado("cliente"), "cuentas_stripe/pro")));
    await assertFails(getDocs(collection(verificado("cliente"), "cuentas_stripe")));
    await assertFails(getDocs(collection(verificado("pro"), "cuentas_stripe")));
    await assertFails(getDoc(doc(sinVerificar("pro"), "cuentas_stripe/pro")));
  });

  it("nadie escribe la cuenta de Stripe desde la app, tampoco el administrador", async () => {
    for (const [quien, db] of [["pro", verificado("pro")], ["admin", comoAdmin()]]) {
      await assertFails(setDoc(doc(db, "cuentas_stripe/nueva"), cuenta()), `${quien}: crear`);
      await assertFails(updateDoc(doc(db, "cuentas_stripe/pro"), { chargesEnabled: true, payoutsEnabled: true }),
        `${quien}: habilitar cobros`);
      await assertFails(updateDoc(doc(db, "cuentas_stripe/pro"), { accountId: "acct_otro" }), `${quien}: cuenta`);
      await assertFails(deleteDoc(doc(db, "cuentas_stripe/pro")), `${quien}: borrar`);
    }
    // Ni crearse la propia apuntando a la cuenta de Stripe de otro profesional
    await assertFails(setDoc(doc(verificado("cliente"), "cuentas_stripe/cliente"), cuenta()));
  });

  it("los eventos de Stripe y los límites de pagos solo los usa el servidor", async () => {
    for (const [quien, db] of [["cliente", verificado("cliente")], ["admin", comoAdmin()]]) {
      await assertFails(getDoc(doc(db, "eventos_stripe/evt_1")), `${quien}: leer evento`);
      await assertFails(getDocs(collection(db, "eventos_stripe")), `${quien}: listar eventos`);
      await assertFails(setDoc(doc(db, "eventos_stripe/evt_2"), { tipo: "x" }), `${quien}: crear evento`);
      await assertFails(deleteDoc(doc(db, "eventos_stripe/evt_1")), `${quien}: borrar evento`);
      await assertFails(getDoc(doc(db, "limites_pagos/cliente")), `${quien}: leer límite`);
      await assertFails(setDoc(doc(db, "limites_pagos/cliente"), { solicitudes: 0, dia: 20261118 }),
        `${quien}: reiniciar límite`);
      await assertFails(deleteDoc(doc(db, "limites_pagos/cliente")), `${quien}: borrar límite`);
      await assertFails(setDoc(doc(db, "limites_pagos/cliente/dias/20261118"), { n: 0 }),
        `${quien}: subcolección del límite`);
    }
  });

  // Reserva de cada transferencia o reembolso (la que impide disputar mientras
  // se mueve el dinero) y avisos al administrador: si la app pudiera borrar una
  // reserva, se colaría una disputa con la transferencia ya en marcha
  it("las reservas de operaciones con dinero y los avisos al administrador solo los usa el servidor", async () => {
    for (const [quien, db] of [["cliente", verificado("cliente")], ["pro", verificado("pro")], ["admin", comoAdmin()]]) {
      await assertFails(getDoc(doc(db, "operaciones_pagos/p1")), `${quien}: leer reserva`);
      await assertFails(getDocs(collection(db, "operaciones_pagos")), `${quien}: listar reservas`);
      await assertFails(setDoc(doc(db, "operaciones_pagos/p2"), { operacion: "reembolso", renovado: 0 }),
        `${quien}: crear reserva`);
      await assertFails(updateDoc(doc(db, "operaciones_pagos/p1"), { renovado: 0 }), `${quien}: soltar reserva`);
      await assertFails(deleteDoc(doc(db, "operaciones_pagos/p1")), `${quien}: borrar reserva`);
      await assertFails(getDoc(doc(db, "avisos_admin_pagos/p1_reembolso_fallido_re_1")), `${quien}: leer aviso`);
      await assertFails(getDocs(collection(db, "avisos_admin_pagos")), `${quien}: listar avisos`);
      await assertFails(deleteDoc(doc(db, "avisos_admin_pagos/p1_reembolso_fallido_re_1")), `${quien}: borrar aviso`);
      await assertFails(setDoc(doc(db, "avisos_admin_pagos/otro"), { asunto: "x" }), `${quien}: crear aviso`);
      // Cola de limpieza de Stripe que deja el borrado de una cuenta: si la app
      // pudiera crearla, haría anular cobros o borrar Customers ajenos
      await assertFails(getDoc(doc(db, "limpieza_stripe/l1")), `${quien}: leer limpieza`);
      await assertFails(getDocs(collection(db, "limpieza_stripe")), `${quien}: listar limpiezas`);
      await assertFails(setDoc(doc(db, "limpieza_stripe/l2"), { cobros: ["pi_ajeno"], customerId: "cus_ajeno" }),
        `${quien}: crear limpieza`);
      await assertFails(deleteDoc(doc(db, "limpieza_stripe/l1")), `${quien}: borrar limpieza`);
    }
  });
});

// Cola /limpieza_storage (T-I1): lo que Storage no dejó borrar al borrar una
// cuenta o un mensaje. Solo la usan las Cloud Functions y cae en el deny-all:
// si la app pudiera crear un encargo, haría borrar archivos de otros chats o
// perfiles; si pudiera borrarlo, los archivos de una cuenta borrada se quedarían.
describe("cola de limpieza de Storage", () => {
  const encargo = { prefijo: "chats/ajeno/", creado: 1, fallos: 0, proximoIntento: 0, procesandoHasta: 0 };

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "limpieza_storage/l1"), encargo);
    });
  });

  it("nadie la lee, la lista ni la escribe desde la app, tampoco el administrador", async () => {
    for (const [quien, db] of [["cliente", verificado("cliente")], ["pro", verificado("pro")],
                               ["sin verificar", sinVerificar("cliente")], ["admin", comoAdmin()],
                               ["sin sesión", env.unauthenticatedContext().firestore()]]) {
      await assertFails(getDoc(doc(db, "limpieza_storage/l1")), `${quien}: leer`);
      await assertFails(getDocs(collection(db, "limpieza_storage")), `${quien}: listar`);
      await assertFails(setDoc(doc(db, "limpieza_storage/l2"), { ...encargo, prefijo: "perfiles/pro/" }),
        `${quien}: crear`);
      await assertFails(updateDoc(doc(db, "limpieza_storage/l1"), { prefijo: "perfiles/pro/" }), `${quien}: cambiar`);
      await assertFails(updateDoc(doc(db, "limpieza_storage/l1"), { requiereAdmin: true }), `${quien}: parar`);
      await assertFails(deleteDoc(doc(db, "limpieza_storage/l1")), `${quien}: borrar`);
    }
  });
});
