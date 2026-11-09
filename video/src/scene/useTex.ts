import { useEffect, useState } from "react";
import { cancelRender, continueRender, delayRender, staticFile } from "remotion";
import * as THREE from "three";

export const TEXTURE_FILES = [
  "0-ei.png",
  "1-schlupf.png",
  "2-baby.png",
  "3-kind.png",
  "4-teen.png",
  "5-erwachsen.png",
] as const;

const loaded = new Map<string, THREE.Texture>();

const load = async (file: string) => {
  const t = await new THREE.TextureLoader().loadAsync(staticFile(file));
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  t.minFilter = THREE.LinearMipmapLinearFilter;
  loaded.set(file, t);
};

/**
 * Lädt alle Texturen VOR dem Mounten des ThreeCanvas.
 * (Das Canvas rendert nur bei Frame-Wechsel neu; spät eintreffende Texturen würden sonst fehlen.)
 */
export const usePreloadTextures = () => {
  const [ready, setReady] = useState(() => TEXTURE_FILES.every((f) => loaded.has(f)));
  const [handle] = useState(() => (ready ? null : delayRender("Texturen laden")));
  useEffect(() => {
    if (ready || handle === null) return;
    Promise.all(TEXTURE_FILES.filter((f) => !loaded.has(f)).map(load))
      .then(() => {
        setReady(true);
        continueRender(handle);
      })
      .catch((e) => cancelRender(e));
  }, [ready, handle]);
  return ready;
};

/** Synchroner Zugriff innerhalb der Szene (nach usePreloadTextures). */
export const useTex = (file: string): THREE.Texture => {
  const t = loaded.get(file);
  if (!t) throw new Error(`Textur ${file} nicht vorgeladen`);
  return t;
};
