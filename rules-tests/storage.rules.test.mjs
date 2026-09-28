// Tests de storage.rules (con reglas cruzadas contra Firestore). Ejecutar desde
// esta carpeta: npm install && npm test
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { deleteObject, getMetadata, ref, uploadBytes } from "firebase/storage";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-zerohaus",
    firestore: { rules: readFileSync("../firestore.rules", "utf8") },
    storage: { rules: readFileSync("../storage.rules", "utf8") },
  });
});

after(async () => env?.cleanup());

const verificado = (uid) =>
  env.authenticatedContext(uid, { email: `${uid}@x.es`, email_verified: true }).storage();
const sinVerificar = (uid) =>
  env.authenticatedContext(uid, { email: `${uid}@x.es`, email_verified: false }).storage();

const imagen = new Uint8Array([0xff, 0xd8, 0xff, 0xe0]);
const jpeg = { contentType: "image/jpeg" };

beforeEach(async () => {
  await env.clearFirestore();
  await env.clearStorage();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "chats/c1"), { participantes: ["cliente", "pro"] });
    // Foto que el profesional ya envió por el chat
    await uploadBytes(ref(ctx.storage(), "chats/c1/m1.jpg"), imagen, jpeg);
  });
});

describe("archivos del chat", () => {
  it("un participante envía un archivo nuevo", async () => {
    await assertSucceeds(uploadBytes(ref(verificado("cliente"), "chats/c1/m2.jpg"), imagen, jpeg));
  });

  it("un participante no puede sobrescribir lo que envió el otro", async () => {
    await assertFails(uploadBytes(ref(verificado("cliente"), "chats/c1/m1.jpg"), imagen, jpeg));
  });

  it("un participante no puede borrar lo que envió el otro", async () => {
    await assertFails(deleteObject(ref(verificado("cliente"), "chats/c1/m1.jpg")));
  });

  it("solo los participantes ven los archivos", async () => {
    await assertSucceeds(getMetadata(ref(verificado("pro"), "chats/c1/m1.jpg")));
    await assertFails(getMetadata(ref(verificado("otro"), "chats/c1/m1.jpg")));
  });

  it("alguien ajeno al chat no puede subir archivos", async () => {
    await assertFails(uploadBytes(ref(verificado("otro"), "chats/c1/m3.jpg"), imagen, jpeg));
  });

  it("sin el email verificado no se sube nada", async () => {
    await assertFails(uploadBytes(ref(sinVerificar("cliente"), "chats/c1/m4.jpg"), imagen, jpeg));
  });
});

describe("fotos de perfil", () => {
  it("cada uno sube y cambia su propia foto", async () => {
    const st = verificado("cliente");
    await assertSucceeds(uploadBytes(ref(st, "perfiles/cliente/foto_perfil"), imagen, jpeg));
    await assertSucceeds(uploadBytes(ref(st, "perfiles/cliente/foto_perfil"), imagen, jpeg));
  });

  it("no se puede cambiar la foto de otro", async () => {
    await assertFails(uploadBytes(ref(verificado("cliente"), "perfiles/pro/foto_perfil"), imagen, jpeg));
  });

  it("la foto de perfil tiene que ser una imagen", async () => {
    await assertFails(uploadBytes(ref(verificado("cliente"), "perfiles/cliente/foto_perfil"),
      imagen, { contentType: "application/x-msdownload" }));
  });
});
