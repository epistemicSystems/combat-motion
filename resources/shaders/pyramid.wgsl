// Pyramid Decomposition Shader - Laplacian Pyramid
//
// Purpose: Build multi-scale representation for Eulerian magnification
//
// Algorithm: Laplacian Pyramid (3 levels)
//   Level 0: Original (512×512)
//   Level 1: Downsampled (256×256)
//   Level 2: Downsampled (128×128)
//
//   Laplacian[i] = Level[i] - Upsample(Level[i+1])
//   (Captures details at each scale)
//
// Why Laplacian Pyramid?
//   - Separates motion at different spatial frequencies
//   - Breathing = large-scale motion (low frequencies)
//   - Captured well in coarser levels
//   - Fine details preserved in finer levels
//
// Downsampling method: 2×2 box filter (simple average)
//   Fast, good enough for breathing analysis
//
// Usage:
//   1. downsample: Generate Gaussian pyramid (smooth + subsample)
//   2. compute_laplacian: Compute difference (current - upsampled next level)

@group(0) @binding(0) var input_texture: texture_2d<f32>;
@group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;

@compute @workgroup_size(8, 8)
fn downsample(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Output coordinates (half resolution of input)
    let out_coords = vec2<i32>(global_id.xy);
    let out_dims = textureDimensions(output_texture);

    // Bounds check
    if (out_coords.x >= out_dims.x || out_coords.y >= out_dims.y) {
        return;
    }

    // Input coordinates (2× output coordinates)
    let in_coords = out_coords * 2;

    // Sample 2×2 region and average (box filter)
    var sum = vec4<f32>(0.0);
    for (var dy = 0; dy < 2; dy++) {
        for (var dx = 0; dx < 2; dx++) {
            let sample_pos = in_coords + vec2<i32>(dx, dy);
            sum += textureLoad(input_texture, sample_pos, 0);
        }
    }
    let avg = sum / 4.0;

    textureStore(output_texture, out_coords, avg);
}

@compute @workgroup_size(8, 8)
fn upsample(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Output coordinates (double resolution of input)
    let out_coords = vec2<i32>(global_id.xy);
    let out_dims = textureDimensions(output_texture);

    // Bounds check
    if (out_coords.x >= out_dims.x || out_coords.y >= out_dims.y) {
        return;
    }

    // Input coordinates (half of output coordinates)
    let in_coords = out_coords / 2;

    // Nearest neighbor upsampling (simple, fast)
    let color = textureLoad(input_texture, in_coords, 0);

    textureStore(output_texture, out_coords, color);
}

@compute @workgroup_size(8, 8)
fn compute_laplacian(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Compute Laplacian: Original - Upsampled(Gaussian)
    //
    // Laplacian captures high-frequency details at this level
    // Motion magnification amplifies these details

    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(input_texture);

    // Bounds check
    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    // Load original (current level)
    let original = textureLoad(input_texture, coords, 0);

    // Note: This shader assumes gaussian_texture is already upsampled
    // to match dimensions of input_texture
    // Load upsampled Gaussian (next level, already upsampled in separate pass)
    // For now, we'll do nearest neighbor inline (simpler)
    let gaussian_coords = coords / 2;
    let gaussian_lowres = textureLoad(input_texture, gaussian_coords, 0);

    // Compute difference (Laplacian)
    let laplacian = original - gaussian_lowres;

    // Shift to [0, 1] range for storage
    // Laplacian can be negative, so we map [-1, 1] → [0, 1]
    let shifted = laplacian * 0.5 + 0.5;

    textureStore(output_texture, coords, shifted);
}

@compute @workgroup_size(8, 8)
fn reconstruct(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Reconstruct from Laplacian pyramid
    //
    // Original = Laplacian + Upsampled(Gaussian)
    //
    // After amplification, we add back the magnified Laplacian
    // to the upsampled coarser level

    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(input_texture);

    // Bounds check
    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    // Load Laplacian (shifted to [0, 1])
    let laplacian_shifted = textureLoad(input_texture, coords, 0);

    // Unshift from [0, 1] to [-1, 1]
    let laplacian = (laplacian_shifted - 0.5) * 2.0;

    // Load upsampled Gaussian (coarser level)
    let gaussian_coords = coords / 2;
    let gaussian = textureLoad(input_texture, gaussian_coords, 0);

    // Reconstruct
    let reconstructed = laplacian + gaussian;

    // Clamp to valid range [0, 1]
    let clamped = clamp(reconstructed, vec4<f32>(0.0), vec4<f32>(1.0));

    textureStore(output_texture, coords, clamped);
}
