// Gaussian Blur Shader - Separable Implementation
//
// Purpose: Apply Gaussian blur to reduce noise before motion magnification
//
// Algorithm: Separable Gaussian filter (5-tap kernel, sigma ≈ 1.0)
//   1. Horizontal pass: Blur along X axis
//   2. Vertical pass: Blur along Y axis
//
// Separability advantage:
//   Non-separable 5×5: 25 texture reads per pixel
//   Separable (5 + 5): 10 texture reads per pixel
//   Speedup: 2.5x
//
// Kernel coefficients (5-tap, sigma=1.0):
//   [0.06136, 0.24477, 0.38774, 0.24477, 0.06136]
//   Sum = 1.0 (normalized)
//
// Usage:
//   Pass 1: horizontal_blur (input → temp)
//   Pass 2: vertical_blur (temp → output)

@group(0) @binding(0) var input_texture: texture_2d<f32>;
@group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;

// Gaussian kernel (5-tap, sigma=1.0)
// Generated from: exp(-(x^2) / (2 * sigma^2)) / sqrt(2 * pi * sigma^2)
const KERNEL: array<f32, 5> = array<f32, 5>(
    0.06136, 0.24477, 0.38774, 0.24477, 0.06136
);

@compute @workgroup_size(8, 8)
fn horizontal_blur(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(input_texture);

    // Bounds check (GPU threads may exceed texture size)
    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    var sum = vec4<f32>(0.0);

    // Apply horizontal kernel (5 taps centered at current pixel)
    for (var i = 0; i < 5; i++) {
        let offset = i - 2; // Center kernel: [-2, -1, 0, 1, 2]
        let sample_x = clamp(coords.x + offset, 0, dims.x - 1);
        let color = textureLoad(input_texture, vec2<i32>(sample_x, coords.y), 0);
        sum += color * KERNEL[i];
    }

    textureStore(output_texture, coords, sum);
}

@compute @workgroup_size(8, 8)
fn vertical_blur(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(input_texture);

    // Bounds check
    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    var sum = vec4<f32>(0.0);

    // Apply vertical kernel (5 taps centered at current pixel)
    for (var i = 0; i < 5; i++) {
        let offset = i - 2; // Center kernel: [-2, -1, 0, 1, 2]
        let sample_y = clamp(coords.y + offset, 0, dims.y - 1);
        let color = textureLoad(input_texture, vec2<i32>(coords.x, sample_y), 0);
        sum += color * KERNEL[i];
    }

    textureStore(output_texture, coords, sum);
}
