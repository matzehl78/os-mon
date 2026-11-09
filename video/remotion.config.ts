import { Config } from "@remotion/cli/config";

// WebGL im Headless-Chrome: "angle" nutzt die GPU, ohne GPU (Server, CI) mit --gl=swangle rendern.
Config.setChromiumOpenGlRenderer("angle");
Config.setVideoImageFormat("jpeg");
Config.setJpegQuality(92);
Config.setCodec("h264");
Config.setCrf(18);
Config.setPixelFormat("yuv420p");
// Eigener Chrome (z. B. Playwright-Headless-Shell): REMOTION_BROWSER=/pfad/zu/chrome-headless-shell
if (process.env.REMOTION_BROWSER) Config.setBrowserExecutable(process.env.REMOTION_BROWSER);
