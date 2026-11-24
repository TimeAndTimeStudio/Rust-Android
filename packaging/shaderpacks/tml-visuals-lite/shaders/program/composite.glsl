// TML Visuals Lite: the sky and the air. Pixels at the far plane get the sky, the sun and the other
// bodies (colortex2: stars and moon over black) in place of the game's own sky; the others the haze
// they are seen through, thicker low down, and past most of the render distance the sky itself.
// The alpha is how much of the sky shows there, which `final` follows to the sun for its rays.

#include "/lib/common.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex2Format = RGBA16F;
const bool colortex2Clear = true;
*/

#ifdef VERTEX_SHADER

noperspective out vec2 texcoord;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    gl_Position = ftransform();
}

#endif

#ifdef FRAGMENT_SHADER

noperspective in vec2 texcoord;

uniform sampler2D colortex0;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform vec3 cameraPosition;
uniform ivec2 eyeBrightnessSmooth;
uniform float frameTimeCounter;

// The haze is FOG_DENSITY thick at sea level and thins by e every FOG_HEIGHT blocks above it.
const float FOG_FLOOR = 63.0;
const float FOG_HEIGHT = 40.0;

// How much haze a ray `rel` long, from the eye at height `y0`, crosses: thicker at dawn and dusk
// and in rain, and no more than twice sea level's deep underground.
float hazeDepth(vec3 rel, float y0, vec3 s) {
    float lowSun = 1.0 - smoothstep(0.0, 0.35, abs(s.y));
    float density = FOG_DENSITY * 0.003 * (1.0 + 0.5 * lowSun + 4.0 * rainStrength);
    float e0 = exp(-(y0 - FOG_FLOOR) / FOG_HEIGHT);
    float k = -rel.y / FOG_HEIGHT;
    // The mean of exp(-(y - FOG_FLOOR) / FOG_HEIGHT) along the ray.
    float mean = abs(k) > 1e-3 ? e0 * (exp(k) - 1.0) / k : e0;
    return density * min(mean, 2.0) * length(rel);
}

// The light the haze along `d` sends to the eye: the sky's near the horizon (looking down at a
// valley, the sunlit air is as bright as the horizon), dark where the eye has no sky above.
vec3 hazeLight(vec3 d, vec3 s) {
    vec3 level = normalize(vec3(d.x, max(d.y, 0.0) + 0.001, d.z));
    float eyeSky = float(eyeBrightnessSmooth.y) / 240.0;
    return skyRadiance(level, s) * mix(0.05, 1.0, eyeSky * eyeSky);
}

// A soft cloud layer of its own, so the default look has clouds whether vanilla's cloud
// option is on or off. `gbuffers_clouds` discards the game's mesh for this.
float cloudHash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float cloudNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = cloudHash(i);
    float b = cloudHash(i + vec2(1.0, 0.0));
    float c = cloudHash(i + vec2(0.0, 1.0));
    float d = cloudHash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float cloudFbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 2; i++) {
        sum += amp * cloudNoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return sum;
}

// One detail octave under the billows: wisps at the edges, not another full fbm.
float cloudDensity(vec2 p, float t) {
    float base = cloudFbm(p) * 0.72 + 0.14;
    float detail = cloudNoise(p * 3.1 + vec2(t * 0.0035, -t * 0.0012));
    return base + (detail - 0.5) * 0.16;
}

// The cloud colour and how much of the sky it covers along `dir`, at `height`. `maxDistance`
// is how far the ray reaches before something else (the world) is in front of it; `scale` and
// `speed` let a second, higher band move as its own sheet.
vec4 cloudLayer(vec3 dir, vec3 s, float height, float maxDistance,
                float scale, float speed, float opacity) {
    if (CLOUDS_AMOUNT <= 0.0 || opacity <= 0.0 || isEyeInWater != 0 || dir.y <= 0.0) {
        return vec4(0.0);
    }
    float t = (height - cameraPosition.y) / dir.y;
    if (t <= 0.0 || t >= maxDistance) {
        return vec4(0.0);
    }
    vec2 q = (cameraPosition.xz + dir.xz * t) * 0.0045 * scale;
    // Vanilla's drift (about 0.6 blocks a second here, 0.2 across): `q` is world blocks times
    // 0.0045, so this was 178 blocks a second and the whole sky raced past.
    q += vec2(frameTimeCounter * CLOUD_SPEED * 0.0027,
              frameTimeCounter * CLOUD_SPEED * 0.0009) * speed;
    float n = cloudDensity(q, frameTimeCounter);
    // More coverage lowers the threshold: the same sky, more or fewer clouds.
    float threshold = mix(0.68, 0.34, CLOUD_COVERAGE);
    float cover = smoothstep(threshold, threshold + 0.15, n) * CLOUDS_AMOUNT * opacity;
    // Show the layer down to the horizon, and fade only a very long ray: the layer is above the
    // world, so the render distance must not cut it to a patch overhead.
    cover *= smoothstep(0.0, 0.055, dir.y);
    cover *= 1.0 - smoothstep(3000.0, 9000.0, t);
    if (cover <= 0.0) {
        return vec4(0.0);
    }
    float day = daylight(s);
    vec3 shadowColor = mix(vec3(0.30, 0.35, 0.46), vec3(0.55, 0.58, 0.66), day);
    vec3 litColor = mix(vec3(1.0, 0.68, 0.46), vec3(1.0, 0.99, 0.96),
                        smoothstep(-0.05, 0.35, s.y));
    float lit = smoothstep(threshold + 0.02, threshold + 0.30, n);
    vec3 color = mix(shadowColor, litColor, lit);
    // Darker undersides where another cloud sits between this one and the sun.
    vec2 towardSun = normalize(vec2(s.x, s.z) + vec2(0.0001));
    float under = cloudNoise(q * 0.9 + towardSun * 0.35);
    color *= mix(0.76, 1.0, smoothstep(0.30, 0.82, under));
    color *= mix(0.55, 1.0, max(s.y, 0.0) * 0.6 + 0.4);
    // A silver lining on the sunward edge, brighter through thinner cloud.
    float sunward = pow(max(dot(dir, s), 0.0), 12.0);
    color += sunlight(s) * sunward * mix(0.55, 0.18, lit);
    // Sunrise and sunset paint the whole layer with the sky.
    color = mix(color, skyRadiance(dir, s) * 1.12, (1.0 - day) * 0.35);
    return vec4(color, cover);
}

// The two sheets the default look draws: the vanilla layer's height and a higher, thinner one
// whose slower drift makes the sky read as depth rather than a single flat band.
vec4 cloudsAlong(vec3 dir, vec3 s, float maxDistance) {
    vec4 low = cloudLayer(dir, s, 192.33, maxDistance, 1.0, 1.0, 1.0);
    vec4 high = cloudLayer(dir, s, 258.0, maxDistance, 1.55, 0.55, 0.62);
    return vec4(mix(low.rgb, high.rgb, high.a), low.a + high.a * (1.0 - low.a));
}

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;
    vec3 s = sunDir();
    float sky = 1.0;
    if (depth >= 1.0) {
        // The near plane: the far one is at infinity, where w is 0.
        vec4 view = gbufferProjectionInverse * vec4(texcoord * 2.0 - 1.0, -1.0, 1.0);
        vec3 dir = normalize(mat3(gbufferModelViewInverse) * (view.xyz / view.w));
        // A round sun, soft at its rim (the bloom makes its glow), and the moon and stars.
        float disk = smoothstep(0.99895, 0.99935, dot(dir, s)) * (1.0 - rainStrength);
        color = skyRadiance(dir, s) + sunlight(s) * disk * 8.0 + texture2D(colortex2, texcoord).rgb;
        vec4 clouds = cloudsAlong(dir, s, 1.0e30);
        color = mix(color, clouds.rgb, clouds.a);
        if (isEyeInWater == 1) color = vec3(0.02, 0.11, 0.15) * (0.15 + 0.85 * daylight(s));
    } else {
        vec4 view = gbufferProjectionInverse * vec4(vec3(texcoord, depth) * 2.0 - 1.0, 1.0);
        vec3 rel = (gbufferModelViewInverse * vec4(view.xyz / view.w, 1.0)).xyz;
        float dist = length(rel);
        vec3 dir = rel / max(dist, 1e-4);
        if (isEyeInWater == 1) {
            vec3 water = vec3(0.02, 0.11, 0.15) * (0.15 + 0.85 * daylight(s));
            color = mix(color, water, 1.0 - exp(-dist * 0.07));
        } else if (isEyeInWater == 2) {
            color = mix(color, vec3(0.9, 0.25, 0.02), 1.0 - exp(-dist * 0.8));
        } else if (isEyeInWater == 3) {
            color = mix(color, vec3(0.55, 0.62, 0.70), 1.0 - exp(-dist * 0.6));
        } else {
            float haze = 1.0 - exp(-hazeDepth(rel, cameraPosition.y, s));
            color = mix(color, hazeLight(dir, s), haze);
            // The world's edge fades into the sky behind it.
            float edge = smoothstep(far * 0.7, far, length(rel.xz));
            color = mix(color, skyRadiance(dir, s), edge);
            sky = edge * edge;
        }
        // The sheets are above the world: where the ray meets one before the ground it is in
        // front of what is drawn, so it shows over terrain and trees too, not only in the sky.
        if (isEyeInWater == 0 && dir.y > 0.0) {
            vec4 clouds = cloudsAlong(dir, s, dist);
            color = mix(color, clouds.rgb, clouds.a);
        }
    }

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color, isEyeInWater == 0 ? sky : 0.0);
}

#endif
