import React from "react";
import { useThree } from "@react-three/fiber";
import { Easing, interpolate, useCurrentFrame } from "remotion";
import { Creature } from "./Creature";
import { Egg } from "./Egg";
import { Bursts, Glows, Sparkles, Stars, energyAt } from "./Effects";
import { BURSTS, DURATION, EGG, loopSin, ramp } from "../timeline";

/** Kamerafahrt: Push-in auf das wackelnde Ei, Rückzug beim Wachsen, Shake bei jedem Burst. Frame 0 == Frame DURATION. */
const CameraRig: React.FC = () => {
  const frame = useCurrentFrame();
  const camera = useThree((s) => s.camera);
  const z = interpolate(
    frame,
    [0, EGG.shakeStart, EGG.burst - 2, EGG.burst + 10, 300, 540, 576, DURATION],
    [10.2, 10.2, 7.4, 9.0, 9.6, 10.4, 9.0, 10.2],
    { easing: Easing.inOut(Easing.sin) },
  );
  let shake = 0;
  for (const b of BURSTS) if (frame >= b) shake = Math.max(shake, 1 - ramp(frame, b, b + 14));
  shake += ramp(frame, EGG.crackStart, EGG.burst) * (frame < EGG.burst ? 0.35 : 0);
  const sx = Math.sin(frame * 3.1) * 0.06 * shake;
  const sy = Math.cos(frame * 2.7) * 0.06 * shake;
  const orbitX = 0.9 * loopSin(frame, 1);
  const orbitY = 0.35 * loopSin(frame, 2, 1);
  camera.position.set(orbitX + sx, 0.45 + orbitY + sy, z);
  camera.lookAt(0, 0.15, 0);
  // Energie -> leichter FOV-Kick
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (camera as any).fov = 34 + energyAt(frame) * 2.5;
  camera.updateProjectionMatrix();
  return null;
};

export const Scene: React.FC = () => (
  <>
    <CameraRig />
    <ambientLight intensity={0.5} color="#ffd9f6" />
    <directionalLight position={[3, 5, 6]} intensity={1.6} color="#fff0fb" />
    <directionalLight position={[-4, 2, -3]} intensity={1.4} color="#b06bff" />
    <Stars />
    <Glows />
    <Egg />
    <Creature />
    <Sparkles />
    <Bursts />
  </>
);
