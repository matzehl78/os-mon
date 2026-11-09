import { Config } from "@remotion/cli/config";

Config.setVideoImageFormat("jpeg");
Config.setJpegQuality(95);
Config.setCodec("h264");
Config.setCrf(16);
Config.setPixelFormat("yuv420p");
// WebGL im Headless-Browser: "angle" nutzt die GPU. Ohne GPU (Server/CI): --gl=swangle
Config.setChromiumOpenGlRenderer("angle");
Config.setConcurrency(null);
