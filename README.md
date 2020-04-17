# Shader Builder

Using the power of Kotlin you can build Shaders in a declarative and flexible way.

This class depends on native library GLEW.

## Example

```kt
val shader = Shader.building {
    vertexSource = """
        #version 330 core
        layout(location = 0) in vec3 vertexPosition_modelspace;
        layout(location = 1) in vec2 vertexUV;
        vec4 computeTransform(mat4 mvp, vec3 pos) {
            return mvp * vec4(pos, 1);
        }
        out vec2 UV;
        uniform mat4 MVP;
        void main(){
            gl_Position = computeTransform(MVP,vertexPosition_modelspace);
            UV = vertexUV;
        }

    """
    fragmentSource = """
        #version 330 core
        in vec2 UV;
        out vec3 color;
        uniform sampler2D textureSampler;
        void main(){
            color = texture( textureSampler, UV ).rgb;
        }
    """
}
```
