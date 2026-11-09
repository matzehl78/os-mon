import React from "react";
import { AbsoluteFill, useVideoConfig } from "remotion";
import { ThreeCanvas } from "@remotion/three";
import { Scene } from "./scene/Scene";
import { Overlay } from "./Overlay";
import { usePreloadTextures } from "./scene/useTex";

export const Evolution: React.FC = () => {
  const { width, height } = useVideoConfig();
  const ready = usePreloadTextures();
  return (
    <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 42%, #3a0b52 0%, #1a0630 45%, #07010f 100%)" }}>
      {ready && (
      <ThreeCanvas
        width={width}
        height={height}
        camera={{ fov: 34, near: 0.1, far: 100, position: [0, 0.45, 10.2] }}
        gl={{ antialias: true, alpha: true, preserveDrawingBuffer: true }}
        dpr={1}
      >
        <Scene />
      </ThreeCanvas>
      )}
      <Overlay />
    </AbsoluteFill>
  );
};
