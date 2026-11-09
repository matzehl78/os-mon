import React from "react";
import { Composition } from "remotion";
import { Evolution } from "./Evolution";
import { DURATION, FPS, HEIGHT, WIDTH } from "./timeline";

export const RemotionRoot: React.FC = () => (
  <>
    {/* 9:16 für TikTok / Reels / Shorts – nahtlose Schleife */}
    <Composition id="OsMonEvolution" component={Evolution} durationInFrames={DURATION} fps={FPS} width={WIDTH} height={HEIGHT} />
    {/* 1:1 für Feed-Posts */}
    <Composition id="OsMonEvolutionSquare" component={Evolution} durationInFrames={DURATION} fps={FPS} width={1080} height={1080} />
  </>
);
