// Amplification Shader - Eulerian Video Magnification
//
// Purpose: Amplify subtle motion to make it visible
//
// Algorithm:
//   Output = Original + Gain × Motion
//
//   Where:
//     Original = Input frame
//     Motion = Filtered temporal signal (breathing component)
//     Gain = Amplification factor (typically 20-30×)
//
// Simplified Temporal Processing (LOD 3 MVP):
//   Motion = Current - TemporalMean
//
//   Instead of full IIR bandpass filter, we use simpler approach:
//   - Compute temporal mean (average of all frames)
//   - Subtract mean from current frame
//   - This isolates motion component
//   - Amplify this motion
//
// Why this works for breathing:
//   - Temporal mean captures static background
//   - Subtraction reveals motion
//   - Breathing is low-frequency, large-scale motion
//   - Simple mean subtraction captures it well enough
//
// Clamping:
//   After amplification, values may exceed [0, 1] range
//   Clamp to prevent invalid colors

@group(0) @binding(0) var original_texture: texture_2d<f32>;
@group(0) @binding(1) var motion_texture: texture_2d<f32>;
@group(0) @binding(2) var output_texture: texture_storage_2d<rgba8unorm, write>;

@group(1) @binding(0) var<uniform> params: AmplifyParams;

struct AmplifyParams {
    gain: f32,
}

@compute @workgroup_size(8, 8)
fn amplify(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(original_texture);

    // Bounds check
    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    // Load original frame
    let original = textureLoad(original_texture, coords, 0);

    // Load motion signal (already shifted to [0, 1])
    // Motion was computed as: (current - mean) * 0.5 + 0.5
    let motion_shifted = textureLoad(motion_texture, coords, 0);

    // Unshift motion from [0, 1] to [-1, 1]
    // This restores the actual motion values (positive and negative)
    let motion = (motion_shifted - 0.5) * 2.0;

    // Amplify motion
    let amplified_motion = motion * params.gain;

    // Add amplified motion to original
    let result = original + amplified_motion;

    // Clamp to valid range [0, 1]
    // This prevents color artifacts from out-of-range values
    let clamped = clamp(result, vec4<f32>(0.0), vec4<f32>(1.0));

    textureStore(output_texture, coords, clamped);
}

@compute @workgroup_size(8, 8)
fn subtract_mean(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Compute motion by subtracting temporal mean
    //
    // Motion = Current - Mean
    //
    // This isolates the time-varying component (breathing motion)
    // from the static background

    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(original_texture);

    // Bounds check
    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    // Load current frame
    let current = textureLoad(original_texture, coords, 0);

    // Load temporal mean (average of all frames)
    let mean = textureLoad(motion_texture, coords, 0);

    // Compute motion (difference)
    let motion = current - mean;

    // Shift to [0, 1] range for storage
    // Motion can be negative, so map [-1, 1] → [0, 1]
    let shifted = motion * 0.5 + 0.5;

    textureStore(output_texture, coords, shifted);
}

@compute @workgroup_size(8, 8)
fn identity(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Identity transformation (pass-through)
    // Useful for testing pipeline

    let coords = vec2<i32>(global_id.xy);
    let dims = textureDimensions(original_texture);

    // Bounds check
    if (coords.x >= dims.x || coords.y >= dims.y) {
        return;
    }

    let color = textureLoad(original_texture, coords, 0);
    textureStore(output_texture, coords, color);
}
