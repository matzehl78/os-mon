import { Composition } from "remotion";
import { SchlupfVideo } from "./SchlupfVideo";
import { FPS, FRAMES } from "./timeline";

export const RemotionRoot: React.FC = () => (
  <>
    {/* Hochformat für TikTok, Reels, Shorts */}
    <Composition id="SchlupfVertikal" component={SchlupfVideo} durationInFrames={FRAMES} fps={FPS} width={1080} height={1920}
      defaultProps={{ format: "vertikal" as const, sound: true }} />
    {/* Quadrat für Feed-Posts */}
    <Composition id="SchlupfQuadrat" component={SchlupfVideo} durationInFrames={FRAMES} fps={FPS} width={1080} height={1080}
      defaultProps={{ format: "quadrat" as const, sound: true }} />
  </>
);
