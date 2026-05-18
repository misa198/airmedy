<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { Renderer, Program, Mesh, Triangle } from 'ogl'
import type { ThemeColors } from '../../bindings/airmedy/internal/domain/models'

const props = defineProps<{
  theme: ThemeColors | null,
  isPlaying?: boolean,
}>()
const containerRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

type Vec3 = [number, number, number]

function hexToVec3(hex: string): Vec3 {
  const c = hex.replace('#', '')
  return [
    parseInt(c.slice(0, 2), 16) / 255,
    parseInt(c.slice(2, 4), 16) / 255,
    parseInt(c.slice(4, 6), 16) / 255,
  ]
}

const FALLBACK = {
  c1: [0.16, 0.10, 0.24] as Vec3,
  c2: [0.10, 0.10, 0.18] as Vec3,
  c3: [0.09, 0.13, 0.24] as Vec3,
  base: [0.03, 0.02, 0.06] as Vec3,
}

const MAX_LUMINANCE = 0.85

function luminance(c: Vec3): number {
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
}

function clampLuminance(c: Vec3, maxL: number): Vec3 {
  const l = luminance(c)
  if (l <= maxL) return c
  const scale = maxL / l
  return [c[0] * scale, c[1] * scale, c[2] * scale]
}

function colorsFromTheme(theme: ThemeColors | null) {
  if (!theme) return { ...FALLBACK }
  const c1 = clampLuminance(hexToVec3(theme.vibrant), MAX_LUMINANCE)
  const c2 = clampLuminance(hexToVec3(theme.dominant), MAX_LUMINANCE)
  const c3 = clampLuminance(hexToVec3(theme.muted), MAX_LUMINANCE)
  return {
    c1,
    c2,
    c3,
    base: [c2[0] * 0.2, c2[1] * 0.2, c2[2] * 0.2] as Vec3,
  }
}

const targetColors = ref(colorsFromTheme(props.theme))
watch(() => props.theme, (t) => { targetColors.value = colorsFromTheme(t) })

// --- Shaders ---

const VERTEX = /* glsl */`
  attribute vec2 position;
  void main() {
    gl_Position = vec4(position, 0.0, 1.0);
  }
`

// Simplex 3D noise (Ashima Arts / Stefan Gustavson)
const NOISE_GLSL = /* glsl */`
  vec3 _m289v3(vec3 x){return x-floor(x*(1.0/289.0))*289.0;}
  vec4 _m289v4(vec4 x){return x-floor(x*(1.0/289.0))*289.0;}
  vec4 _perm(vec4 x){return _m289v4(((x*34.0)+1.0)*x);}
  vec4 _tis(vec4 r){return 1.79284291400159-0.85373472095314*r;}
  float snoise(vec3 v){
    const vec2 C=vec2(1.0/6.0,1.0/3.0);
    const vec4 D=vec4(0.0,0.5,1.0,2.0);
    vec3 i=floor(v+dot(v,C.yyy));
    vec3 x0=v-i+dot(i,C.xxx);
    vec3 g=step(x0.yzx,x0.xyz);
    vec3 l=1.0-g;
    vec3 i1=min(g.xyz,l.zxy);
    vec3 i2=max(g.xyz,l.zxy);
    vec3 x1=x0-i1+C.xxx;
    vec3 x2=x0-i2+C.yyy;
    vec3 x3=x0-D.yyy;
    i=_m289v3(i);
    vec4 p=_perm(_perm(_perm(
      i.z+vec4(0.0,i1.z,i2.z,1.0))+
      i.y+vec4(0.0,i1.y,i2.y,1.0))+
      i.x+vec4(0.0,i1.x,i2.x,1.0));
    float n_=0.142857142857;
    vec3 ns=n_*D.wyz-D.xzx;
    vec4 j=p-49.0*floor(p*ns.z*ns.z);
    vec4 x_=floor(j*ns.z);
    vec4 y_=floor(j-7.0*x_);
    vec4 x=x_*ns.x+ns.yyyy;
    vec4 y=y_*ns.x+ns.yyyy;
    vec4 h=1.0-abs(x)-abs(y);
    vec4 b0=vec4(x.xy,y.xy);
    vec4 b1=vec4(x.zw,y.zw);
    vec4 s0=floor(b0)*2.0+1.0;
    vec4 s1=floor(b1)*2.0+1.0;
    vec4 sh=-step(h,vec4(0.0));
    vec4 a0=b0.xzyw+s0.xzyw*sh.xxyy;
    vec4 a1=b1.xzyw+s1.xzyw*sh.zzww;
    vec3 p0=vec3(a0.xy,h.x);
    vec3 p1=vec3(a0.zw,h.y);
    vec3 p2=vec3(a1.xy,h.z);
    vec3 p3=vec3(a1.zw,h.w);
    vec4 norm=_tis(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
    p0*=norm.x;p1*=norm.y;p2*=norm.z;p3*=norm.w;
    vec4 m=max(0.6-vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.0);
    m=m*m;
    return 42.0*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));
  }
`

const FRAGMENT = /* glsl */`
  precision mediump float;
  uniform float uTime;
  uniform vec2  uResolution;
  uniform vec3  uColor1;
  uniform vec3  uColor2;
  uniform vec3  uColor3;
  uniform vec3  uBase;

  ${NOISE_GLSL}

  void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float t = uTime * 0.018;

    // 3 independent noise fields (no domain warp — saves 2 snoise calls).
    // Different frequencies + large spatial/temporal offsets keep them decorrelated.
    float n1 = snoise(vec3(uv * 0.9,                    t + 0.0))  * 0.5 + 0.5;
    float n2 = snoise(vec3(uv * 1.1 + vec2(17.3,  9.1), t + 6.3))  * 0.5 + 0.5;
    float n3 = snoise(vec3(uv * 0.8 + vec2( 8.7, 23.4), t + 12.7)) * 0.5 + 0.5;

    // Softmax: amplifies differences so each region is dominated by one color
    const float SHARP = 8.0;
    float e1 = exp((n1 - 0.5) * SHARP);
    float e2 = exp((n2 - 0.5) * SHARP);
    float e3 = exp((n3 - 0.5) * SHARP);
    float total = e1 + e2 + e3;
    vec3 color = uColor1*(e1/total) + uColor2*(e2/total) + uColor3*(e3/total);

    color = mix(uBase, color, 0.88);
    gl_FragColor = vec4(color, 1.0);
  }
`

// --- WebGL lifecycle ---

let renderer: Renderer | null = null
let rafId = 0
let ro: ResizeObserver | null = null

onMounted(() => {
  const canvas = canvasRef.value!
  renderer = new Renderer({
    canvas,
    alpha: true,
    premultipliedAlpha: false,
    dpr: 1, // soft gradient — retina precision is wasted and costs 4× pixels
  })
  const gl = renderer.gl
  if (!gl) return

  const geometry = new Triangle(gl)

  const init = targetColors.value
  let curC1: Vec3 = [...init.c1]
  let curC2: Vec3 = [...init.c2]
  let curC3: Vec3 = [...init.c3]
  let curBase: Vec3 = [...init.base]

  const program = new Program(gl, {
    vertex: VERTEX,
    fragment: FRAGMENT,
    uniforms: {
      uTime: { value: 0 },
      uResolution: { value: [canvas.clientWidth || 100, canvas.clientHeight || 100] as [number, number] },
      uColor1: { value: curC1 },
      uColor2: { value: curC2 },
      uColor3: { value: curC3 },
      uBase: { value: curBase },
    },
  })

  const mesh = new Mesh(gl, { geometry, program })

  const container = containerRef.value!
  function resize() {
    const DOWNSCALE_FACTOR = 0.15
    const w = container.clientWidth || 100
    const h = container.clientHeight || 100
    renderer!.setSize(w * DOWNSCALE_FACTOR, h * DOWNSCALE_FACTOR)
    canvas.style.width = `${w}px`
    canvas.style.height = `${h}px`
    program.uniforms.uResolution.value = [canvas.width, canvas.height]
    renderer!.render({ scene: mesh })
  }
  ro = new ResizeObserver(resize)
  ro.observe(container)
  resize()

  function lerp3(a: Vec3, b: Vec3, f: number): Vec3 {
    return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f]
  }

  const FRAME_MS = 1000 / 30 // 30 fps cap — motion is too slow to need 60 fps
  let lastTime = 0
  let currentUtime = 0

  function render(time: number) {
    rafId = requestAnimationFrame(render)
    if (time - lastTime < FRAME_MS) return
    const dt = Math.min((time - lastTime) / 1000, 0.1)
    lastTime = time

    // Slow down speed if not playing
    const speed = props.isPlaying ? 1.0 : 0.1
    currentUtime += dt * speed

    const { c1, c2, c3, base } = targetColors.value
    const f = 1 - Math.exp(-dt * 1.5)
    curC1 = lerp3(curC1, c1, f)
    curC2 = lerp3(curC2, c2, f)
    curC3 = lerp3(curC3, c3, f)
    curBase = lerp3(curBase, base, f)

    program.uniforms.uTime.value = currentUtime
    program.uniforms.uColor1.value = curC1
    program.uniforms.uColor2.value = curC2
    program.uniforms.uColor3.value = curC3
    program.uniforms.uBase.value = curBase

    renderer!.render({ scene: mesh })
  }
  rafId = requestAnimationFrame(render)
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  ro?.disconnect()
  renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext()
})
</script>

<template>
  <div ref="containerRef" class="living-container">
    <canvas ref="canvasRef" />
  </div>
</template>

<style scoped>
.living-container {
  position: absolute;
  inset: 0;
}
</style>