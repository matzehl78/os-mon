import * as THREE from "three";

/**
 * Figuren-Bild als Ebene. uWhite blendet zur leuchtenden Silhouette (Verwandlungs-Effekt).
 */
export const makeSpriteMaterial = (map: THREE.Texture) =>
  new THREE.ShaderMaterial({
    uniforms: {
      map: { value: map },
      uWhite: { value: 0 },
      uOpacity: { value: 1 },
      uTint: { value: new THREE.Color("#fff3ff") },
    },
    vertexShader: /* glsl */ `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: /* glsl */ `
      uniform sampler2D map;
      uniform float uWhite;
      uniform float uOpacity;
      uniform vec3 uTint;
      varying vec2 vUv;
      void main() {
        vec4 t = texture2D(map, vUv);
        float a = mix(t.a, smoothstep(0.04, 0.45, t.a), uWhite) * uOpacity;
        if (a < 0.002) discard;
        vec3 c = mix(t.rgb, uTint * 1.6, uWhite);
        gl_FragColor = vec4(c, a);
        #include <colorspace_fragment>
      }`,
    transparent: true,
    depthWrite: false,
    toneMapped: false,
  });

/**
 * Weicher Schein hinter der Figur: Alpha der Textur über eine hohe Mip-Stufe = Blur.
 */
export const makeAuraMaterial = (map: THREE.Texture) =>
  new THREE.ShaderMaterial({
    uniforms: {
      map: { value: map },
      uStrength: { value: 0.4 },
      uColor: { value: new THREE.Color("#c44dff") },
    },
    vertexShader: /* glsl */ `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: /* glsl */ `
      uniform sampler2D map;
      uniform float uStrength;
      uniform vec3 uColor;
      varying vec2 vUv;
      void main() {
        // Blur über Mip-Bias (mehrere versetzte Abtastungen), radial maskiert -> keine Plane-Kanten
        float a = 0.0;
        for (int i = 0; i < 8; i++) {
          float ang = float(i) * 0.785398;
          vec2 o = vec2(cos(ang), sin(ang)) * 0.035;
          a += texture2D(map, vUv + o, 3.0).a;
        }
        a = a / 8.0;
        float mask = smoothstep(0.5, 0.36, length(vUv - 0.5));
        a = pow(a, 1.4) * mask;
        gl_FragColor = vec4(uColor * a * uStrength, 1.0);
        #include <colorspace_fragment>
      }`,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    toneMapped: false,
  });

/**
 * Leuchtende Punkte (Sterne, Funken, Burst). Größe, Farbe, Deckkraft pro Punkt als Attribut.
 */
export const makePointsMaterial = (pixelRatio = 1) =>
  new THREE.ShaderMaterial({
    uniforms: { uScale: { value: 900 * pixelRatio } },
    vertexShader: /* glsl */ `
      attribute float aSize;
      attribute float aAlpha;
      attribute vec3 aColor;
      uniform float uScale;
      varying float vAlpha;
      varying vec3 vColor;
      void main() {
        vAlpha = aAlpha;
        vColor = aColor;
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = aSize * uScale / -mv.z;
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: /* glsl */ `
      varying float vAlpha;
      varying vec3 vColor;
      void main() {
        vec2 d = gl_PointCoord - 0.5;
        float r = length(d) * 2.0;
        float core = smoothstep(1.0, 0.0, r);
        float glow = core * core;
        // kleiner Stern-Kreuz-Glanz
        float cross = max(0.0, 1.0 - abs(d.x) * 14.0) * max(0.0, 1.0 - abs(d.y) * 2.2)
                    + max(0.0, 1.0 - abs(d.y) * 14.0) * max(0.0, 1.0 - abs(d.x) * 2.2);
        float a = (glow + cross * 0.35) * vAlpha;
        gl_FragColor = vec4(vColor * a, 1.0);
        #include <colorspace_fragment>
      }`,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    toneMapped: false,
  });

/** Strahlenkranz hinter der Figur. Drehung um 2·(2π/N) pro Loop ist nahtlos (N und N/2 Strahlen). */
export const RAY_COUNT = 14;
export const makeRaysMaterial = () =>
  new THREE.ShaderMaterial({
    uniforms: {
      uAngle: { value: 0 },
      uIntensity: { value: 0.35 },
      uColor: { value: new THREE.Color("#ff6ad5") },
      uColor2: { value: new THREE.Color("#8a3dff") },
    },
    vertexShader: /* glsl */ `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: /* glsl */ `
      uniform float uAngle;
      uniform float uIntensity;
      uniform vec3 uColor;
      uniform vec3 uColor2;
      varying vec2 vUv;
      const float N = ${RAY_COUNT}.0;
      void main() {
        vec2 p = vUv - 0.5;
        float r = length(p) * 2.0;
        float a = atan(p.y, p.x) + uAngle;
        float rays = pow(0.5 + 0.5 * cos(a * N), 6.0);
        float rays2 = pow(0.5 + 0.5 * cos(a * N * 0.5 + 1.3), 10.0) * 0.6;
        float fall = smoothstep(1.0, 0.05, r) * smoothstep(0.0, 0.18, r);
        float core = smoothstep(0.55, 0.0, r) * 0.55;
        vec3 col = mix(uColor2, uColor, smoothstep(0.8, 0.1, r));
        float v = ((rays + rays2) * fall + core) * uIntensity;
        gl_FragColor = vec4(col * v, 1.0);
        #include <colorspace_fragment>
      }`,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    toneMapped: false,
  });

/** Weicher radialer Schein (Boden-Glow, Lichtball) */
export const makeGlowMaterial = (color: string) =>
  new THREE.ShaderMaterial({
    uniforms: { uColor: { value: new THREE.Color(color) }, uIntensity: { value: 1 } },
    vertexShader: /* glsl */ `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }`,
    fragmentShader: /* glsl */ `
      uniform vec3 uColor;
      uniform float uIntensity;
      varying vec2 vUv;
      void main() {
        float r = length(vUv - 0.5) * 2.0;
        float v = pow(max(0.0, 1.0 - r), 2.2) + pow(max(0.0, 1.0 - r), 12.0) * 2.0;
        gl_FragColor = vec4(uColor * v * uIntensity, 1.0);
        #include <colorspace_fragment>
      }`,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    toneMapped: false,
  });
