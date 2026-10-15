// Файлы экспорта для проверки совместимости с web-клиентом: то же ядро WASM, что в браузере.
//   node export_file.mjs make <файл> <пароль> - файл с двумя записями web-export-1 и web-export-2
//   node export_file.mjs read <файл> <пароль> - названия записей из файла в JSON
import { readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const core = require("../inkvault-core/bindings/wasm/pkg/node/inkvault_wasm.js");
const [command, path, password] = process.argv.slice(2);

const engine = () => new core.VaultSync("0192a3f4-0000-7000-8000-0000000000aa", 1, core.generateKey(), "urn:inkvault:vault:0", core.generateNodeId(), 10000);

if (command === "make") {
  const vault = engine();
  for (const name of ["web-export-1", "web-export-2"]) vault.create("login", JSON.stringify({ name, password: `${name}-secret` }), Date.now());
  writeFileSync(path, vault.exportVault(password, Date.now()));
} else if (command === "read") {
  const vault = engine();
  vault.importVault(readFileSync(path, "utf8"), password, Date.now());
  console.log(JSON.stringify(JSON.parse(vault.records()).map((r) => r.record.fields.name.value).sort()));
} else {
  console.error("команды: make <файл> <пароль>, read <файл> <пароль>");
  process.exitCode = 2;
}
