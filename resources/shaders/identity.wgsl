// Identity Shader - Simple pass-through for testing GPU pipeline
//
// Purpose: Verify that WebGPU context, shader compilation, and compute
//          dispatch all work correctly before implementing complex shaders.
//
// Algorithm: Load pixel from input texture, write to output texture (no changes)
//
// Usage:
//   Input: texture_2d<f32> (read-only)
//   Output: texture_storage_2d<rgba8unorm, write> (write-only)
//
// Expected result: Output texture should be identical to input texture

@group(0) @binding(0) var input_texture: texture_2d<f32>;
@group(0) @binding(1) var output_texture: texture_storage_2d<rgba8unorm, write>;

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Get pixel coordinates from thread ID
    let coords = vec2<i32>(global_id.xy);

    // Get texture dimensions
    let dimensions = textureDimensions(input_texture);

    // Bounds check (GPU threads may exceed texture size)
    if (coords.x >= dimensions.x || coords.y >= dimensions.y) {
        return;
    }

    // Load pixel from input (mip level 0)
    let color = textureLoad(input_texture, coords, 0);

    // Write to output (identity transformation)
    textureStore(output_texture, coords, color);
}
