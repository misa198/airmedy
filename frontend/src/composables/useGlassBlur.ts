import { onMounted, onUnmounted, watch, type Ref } from 'vue'
import { Renderer, Program, Mesh, Triangle, RenderTarget, Texture } from 'ogl'

// Full-screen triangle — UVs: (0,0) bottom-left … (1,1) top-right in WebGL space
// With OGL default flipY=true: texture UV.y=0 = bottom of image, UV.y=1 = top
const VERT = /* glsl */`
attribute vec2 position;
attribute vec2 uv;
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position, 0, 1);
}
`

// Pass 1 — horizontal Gaussian, Y-remapped to bottom slice of artwork
const H_FRAG = /* glsl */`
precision mediump float;
uniform sampler2D tMap;
uniform float uStep;  // blur step in UV space (horizontal)
uniform float uRatio; // panelH / windowH — portion of artwork to show
varying vec2 vUv;

void main() {
  // vUv.y=0 = canvas bottom = artwork bottom (UV.y=0 with flipY)
  float y = vUv.y * uRatio;
  vec2 uv = vec2(vUv.x, y);

  vec4 c = vec4(0.0);
  c += texture2D(tMap, uv + vec2(-4.0*uStep, 0.0)) * 0.0162;
  c += texture2D(tMap, uv + vec2(-3.0*uStep, 0.0)) * 0.0540;
  c += texture2D(tMap, uv + vec2(-2.0*uStep, 0.0)) * 0.1216;
  c += texture2D(tMap, uv + vec2(-1.0*uStep, 0.0)) * 0.1945;
  c += texture2D(tMap, uv)                          * 0.2270;
  c += texture2D(tMap, uv + vec2( 1.0*uStep, 0.0)) * 0.1945;
  c += texture2D(tMap, uv + vec2( 2.0*uStep, 0.0)) * 0.1216;
  c += texture2D(tMap, uv + vec2( 3.0*uStep, 0.0)) * 0.0540;
  c += texture2D(tMap, uv + vec2( 4.0*uStep, 0.0)) * 0.0162;
  gl_FragColor = c;
}
`

// Pass 2 — vertical Gaussian + brightness + gradient alpha fade
const V_FRAG = /* glsl */`
precision mediump float;
uniform sampler2D tMap;
uniform float uStep;       // blur step in UV space (vertical)
uniform float uBrightness;
varying vec2 vUv;

void main() {
  vec4 c = vec4(0.0);
  c += texture2D(tMap, vUv + vec2(0.0, -4.0*uStep)) * 0.0162;
  c += texture2D(tMap, vUv + vec2(0.0, -3.0*uStep)) * 0.0540;
  c += texture2D(tMap, vUv + vec2(0.0, -2.0*uStep)) * 0.1216;
  c += texture2D(tMap, vUv + vec2(0.0, -1.0*uStep)) * 0.1945;
  c += texture2D(tMap, vUv)                          * 0.2270;
  c += texture2D(tMap, vUv + vec2(0.0,  1.0*uStep)) * 0.1945;
  c += texture2D(tMap, vUv + vec2(0.0,  2.0*uStep)) * 0.1216;
  c += texture2D(tMap, vUv + vec2(0.0,  3.0*uStep)) * 0.0540;
  c += texture2D(tMap, vUv + vec2(0.0,  4.0*uStep)) * 0.0162;

  c.rgb *= uBrightness;

  // Extra darkening toward bottom (controls area)
  c.rgb -= (1.0 - smoothstep(0.0, 0.5, vUv.y)) * 0.25;
  c.rgb = clamp(c.rgb, 0.0, 1.0);

  // Alpha: opaque at bottom, transparent above ~80% height
  c.a = 1.0 - smoothstep(0.5, 0.9, vUv.y);

  gl_FragColor = c;
}
`

export function useGlassBlur(
  canvasRef: Ref<HTMLCanvasElement | null>,
  imageUrl: Ref<string | null>,
  panelHeight = 160,
) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let renderer: any = null
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let rt: any = null
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let hMesh: any = null
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let vMesh: any = null
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let artTex: any = null

  function render() {
    if (!renderer || !hMesh || !vMesh || !rt || !artTex?.image) return
    renderer.render({ scene: hMesh, target: rt })
    renderer.render({ scene: vMesh })
  }

  function init(canvas: HTMLCanvasElement) {
    const w = canvas.clientWidth || 300
    canvas.width = w
    canvas.height = panelHeight

    renderer = new Renderer({ canvas, width: w, height: panelHeight, alpha: true })
    const gl = renderer.gl
    gl.clearColor(0, 0, 0, 0)

    const geo = new Triangle(gl)
    rt = new RenderTarget(gl, { width: w, height: panelHeight })

    artTex = new Texture(gl, {
      wrapS: gl.CLAMP_TO_EDGE,
      wrapT: gl.CLAMP_TO_EDGE,
      minFilter: gl.LINEAR,
      magFilter: gl.LINEAR,
    })

    const windowH = window.innerHeight || 300
    const ratio = panelHeight / windowH

    hMesh = new Mesh(gl, {
      geometry: geo,
      program: new Program(gl, {
        vertex: VERT,
        fragment: H_FRAG,
        uniforms: {
          tMap: { value: artTex },
          uStep: { value: 10 / w },
          uRatio: { value: ratio },
        },
        transparent: true,
      }),
    })

    vMesh = new Mesh(gl, {
      geometry: geo,
      program: new Program(gl, {
        vertex: VERT,
        fragment: V_FRAG,
        uniforms: {
          tMap: { value: rt.texture },
          uStep: { value: 10 / panelHeight },
          uBrightness: { value: 0.6 },
        },
        transparent: true,
      }),
    })
  }

  function loadArtwork(url: string) {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      if (!artTex) return
      artTex.image = img
      artTex.needsUpdate = true
      render()
    }
    img.src = url
  }

  onMounted(() => {
    const canvas = canvasRef.value
    if (!canvas) return
    init(canvas)
    if (imageUrl.value) loadArtwork(imageUrl.value)
  })

  watch(imageUrl, (url) => {
    if (!url) return
    if (!renderer && canvasRef.value) init(canvasRef.value)
    loadArtwork(url)
  })

  onUnmounted(() => {
    renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext()
    renderer = null
  })
}
