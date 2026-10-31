package me.misa198.airmedy.ui.navigation

// Simplex noise: Ashima Arts / Stefan Gustavson (as in the desktop shader).
// Same simplex noise fields and color weights as the desktop living background.
internal const val LivingArtworkFragmentShader = """
  precision highp float;
  uniform float uTime;
  uniform vec2  uResolution;
  uniform vec3  uColor1;
  uniform vec3  uColor2;
  uniform vec3  uColor3;
  uniform vec3  uBase;

  
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


  void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    uv.y = 1.0 - uv.y; // Readback rows become top-down Compose image rows.
    float t = uTime * 0.018;

    // 3 independent noise fields (no domain warp — saves 2 snoise calls).
    // Different frequencies + large spatial/temporal offsets keep them decorrelated.
    float n1 = snoise(vec3(uv * 0.9,                    t + 0.0))  * 0.5 + 0.5;
    float n2 = snoise(vec3(uv * 1.1 + vec2(17.3,  9.1), t + 6.3))  * 0.5 + 0.5;
    float n3 = snoise(vec3(uv * 0.8 + vec2( 8.7, 23.4), t + 12.7)) * 0.5 + 0.5;

    // Softmax: amplifies differences so each region is dominated by one color
    const float SHARP = 10.0;
    float e1 = exp((n1 - 0.5) * SHARP);
    float e2 = exp((n2 - 0.5) * SHARP);
    float e3 = exp((n3 - 0.5) * SHARP);
    float total = e1 + e2 + e3;
    vec3 color = uColor1*(e1/total) + uColor2*(e2/total) + uColor3*(e3/total);

    color = mix(uBase, color, 0.88);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color *= 1.0 - smoothstep(0.55, 0.9, luma) * 0.22;
    // Stable, sub-LSB noise breaks up contour bands when quantizing to RGBA8.
    float dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    color += vec3((dither - 0.5) / 255.0);
    gl_FragColor = vec4(color, 1.0);
  }
"""
