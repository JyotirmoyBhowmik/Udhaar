/**
 * Node wrapper for image optimization script
 */
const { spawnSync } = require("child_process");
const path = require("path");

const pyScript = path.join(__dirname, "optimize_images.py");
console.log("Running Python image optimization...");
const result = spawnSync("python", [pyScript], { stdio: "inherit" });

if (result.status !== 0) {
  console.error("Image optimization failed with status:", result.status);
  process.exit(result.status || 1);
} else {
  console.log("Image optimization finished successfully.");
}
