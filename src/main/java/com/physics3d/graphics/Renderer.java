package com.physics3d.graphics;

import com.physics3d.model.CelestialBody;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Handles rendering of the 3D scene using OpenGL (fixed-function GL11).
 *
 * Features an orbital camera:
 *   - Left mouse drag: rotate around the look-at target (azimuth / elevation)
 *   - Scroll wheel:    zoom in / out (camera distance)
 *   - WASD keys:       pan the look-at target in the view plane
 *
 * World coordinates are astronomical (~1e11 m). Those magnitudes lose precision
 * in float GL matrices, so the scene is uniformly scaled down by WORLD_SCALE
 * before being submitted to OpenGL.
 */
public class Renderer {
    private long window;
    private int width;
    private int height;
    private boolean shouldClose;

    // Scale factor applied to world coordinates before rendering (meters -> scene units)
    private static final float WORLD_SCALE = 1e-9f;
    // Minimum drawn radius in scene units so bodies stay visible at system scale
    private static final float MIN_DRAW_RADIUS = 5e9f * WORLD_SCALE;
    // Dodaj te pola na poziomie klasy (za minimalnym promieniem):
    private Vector3f sunPosition = new Vector3f(0, 0, 0);  // Sun position in world space
    private static final Vector3f LIGHT_DIRECTION = new Vector3f(0, 1, 0);  // Up vector for ambient
    private static final float[] AMBIENT_COLOR = {0.3f, 0.3f, 0.3f};
    private static final float[] DIFFUSE_INTENSITY = {0.7f, 0.7f, 0.7f};
    // Sphere tessellation
    private static final int SPHERE_STACKS = 16;
    private static final int SPHERE_SLICES = 24;

    // ---- Orbit camera state ----
    private float camDistance = 800.0f;     // distance from target, in scene units
    private float camAzimuth = 0.0f;        // radians, rotation around Y
    private float camElevation = 0.6f;      // radians, up/down
    private float targetX = 0.0f;
    private float targetY = 0.0f;
    private float targetZ = 0.0f;

    // Mouse drag tracking
    private boolean dragging = false;
    private double lastMouseX;
    private double lastMouseY;

    private final Matrix4f viewMatrix = new Matrix4f();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public Renderer(String title, int width, int height) {
        this.width = width;
        this.height = height;
        this.shouldClose = false;

        initGLFW();
        createWindow(title);
        initOpenGL();
    }

    private void initGLFW() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
    }

    private void createWindow(String title) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(width, height, title, 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                shouldClose = true;
            }
        });

        GLFW.glfwSetWindowCloseCallback(window, w -> shouldClose = true);

        // Track window size so the projection aspect ratio stays correct
        GLFW.glfwSetFramebufferSizeCallback(window, (w, fbWidth, fbHeight) -> {
            this.width = Math.max(1, fbWidth);
            this.height = Math.max(1, fbHeight);
        });

        // Mouse button: start/stop dragging to rotate the camera
        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW.GLFW_PRESS) {
                    dragging = true;
                    double[] mx = new double[1];
                    double[] my = new double[1];
                    GLFW.glfwGetCursorPos(window, mx, my);
                    lastMouseX = mx[0];
                    lastMouseY = my[0];
                } else if (action == GLFW.GLFW_RELEASE) {
                    dragging = false;
                }
            }
        });

        // Cursor movement: rotate camera while dragging
        GLFW.glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (dragging) {
                double dx = xpos - lastMouseX;
                double dy = ypos - lastMouseY;
                lastMouseX = xpos;
                lastMouseY = ypos;

                camAzimuth += (float) dx * 0.005f;
                camElevation += (float) dy * 0.005f;

                // Clamp elevation to avoid flipping over the poles
                float limit = (float) (Math.PI / 2.0 - 0.05);
                if (camElevation > limit) camElevation = limit;
                if (camElevation < -limit) camElevation = -limit;
            }
        });

        // Scroll: zoom in / out
        GLFW.glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            float factor = (float) Math.pow(1.1, -yoffset);
            camDistance *= factor;
            if (camDistance < 10.0f) camDistance = 10.0f;
            if (camDistance > 100000.0f) camDistance = 100000.0f;
        });

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);
    }

    private void initOpenGL() {
        GL.createCapabilities();
        GL11.glClearColor(0.0f, 0.0f, 0.05f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** Handle keyboard panning of the camera target. Called once per frame. */
    private void processInput() {
        float panSpeed = camDistance * 0.02f;

        // Camera forward direction projected onto the XZ plane
        float fx = (float) (Math.cos(camElevation) * Math.sin(camAzimuth));
        float fz = (float) (Math.cos(camElevation) * Math.cos(camAzimuth));
        // Right vector (perpendicular to forward on XZ plane)
        float rx = (float) Math.sin(camAzimuth - Math.PI / 2.0);
        float rz = (float) Math.cos(camAzimuth - Math.PI / 2.0);

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            targetX += fx * panSpeed;
            targetZ += fz * panSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            targetX -= fx * panSpeed;
            targetZ -= fz * panSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            targetX -= rx * panSpeed;
            targetZ -= rz * panSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            targetX += rx * panSpeed;
            targetZ += rz * panSpeed;
        }
    }

    private void setupProjection() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = (float) width / (float) height;
        float near = 1.0f;
        float far = 500000.0f;
        // 60-degree vertical field of view
        float fovY = (float) Math.toRadians(60.0);
        float top = (float) (Math.tan(fovY / 2.0) * near);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        GL11.glFrustum(left, right, bottom, top, near, far);
    }

    private void setupCamera() {
        // Camera position on a sphere around the target
        float cx = targetX + (float) (camDistance * Math.cos(camElevation) * Math.sin(camAzimuth));
        float cy = targetY + (float) (camDistance * Math.sin(camElevation));
        float cz = targetZ + (float) (camDistance * Math.cos(camElevation) * Math.cos(camAzimuth));

        viewMatrix.identity().lookAt(
            cx, cy, cz,
            targetX, targetY, targetZ,
            0.0f, 1.0f, 0.0f
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        viewMatrix.get(matrixBuffer);
        GL11.glLoadMatrixf(matrixBuffer);
    }

    /** Convert float array to FloatBuffer and reset position. */
    private FloatBuffer toFloatBuffer(FloatBuffer buffer, float[] array) {
        buffer.clear();
        buffer.put(array);
        buffer.flip();
        return buffer;
    }

    public void render(List<CelestialBody> bodies) {
        processInput();

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        setupProjection();
        setupCamera();

        // Find sun position first
        for (CelestialBody body : bodies) {
            if ("Sun".equals(body.getName())) {
                Vector3f pos = body.getPosition();
                sunPosition.set(pos.x * WORLD_SCALE, pos.y * WORLD_SCALE, pos.z * WORLD_SCALE);
                break;
            }
        }

        for (CelestialBody body : bodies) {
            Vector3f pos = body.getPosition();
            float x = pos.x * WORLD_SCALE;
            float y = pos.y * WORLD_SCALE;
            float z = pos.z * WORLD_SCALE;
            float r = Math.max(body.getRadius() * WORLD_SCALE, MIN_DRAW_RADIUS);

            float[] baseColor = colorFor(body.getName());

            if ("Sun".equals(body.getName())) {
                // Sun emits light: full brightness
                GL11.glColor3f(baseColor[0], baseColor[1], baseColor[2]);
            } else {
                // Planets: apply Lambertian diffuse lighting from Sun
                Vector3f bodyPos = new Vector3f(x, y, z);
                Vector3f toSun = new Vector3f(sunPosition).sub(bodyPos);
                float distToSun = toSun.length();
                if (distToSun > 0.001f) {
                    toSun.normalize();
                }

                // Surface normal (from center outward) - use approximate normal
                // For a sphere at position bodyPos, normal at surface points outward
                float diffuse = Math.max(0.2f, toSun.dot(new Vector3f(x, y, z).normalize()) * 0.5f + 0.5f);

                // Apply lighting: ambient + diffuse
                float litR = baseColor[0] * (0.3f + diffuse * 0.7f);
                float litG = baseColor[1] * (0.3f + diffuse * 0.7f);
                float litB = baseColor[2] * (0.3f + diffuse * 0.7f);

                GL11.glColor3f(litR, litG, litB);
            }

            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, z);
            drawSphere(r, SPHERE_STACKS, SPHERE_SLICES);
            GL11.glPopMatrix();
        }

        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();
    }


    private float[] colorFor(String name) {
        switch (name) {
            case "Sun":   return new float[]{1.0f, 1.0f, 0.0f};     // Bright yellow
            case "Earth": return new float[]{0.0f, 0.5f, 1.0f};     // Blue
            case "Mars":  return new float[]{1.0f, 0.5f, 0.0f};     // Orange
            default:      return new float[]{0.8f, 0.8f, 0.8f};     // Gray
        }
    }

    /** Draw a unit-based sphere of the given radius using a lat/long mesh of quads. */
    private void drawSphere(float radius, int stacks, int slices) {
        for (int i = 0; i < stacks; i++) {
            double lat0 = Math.PI * (-0.5 + (double) i / stacks);
            double lat1 = Math.PI * (-0.5 + (double) (i + 1) / stacks);
            float y0 = (float) Math.sin(lat0);
            float y1 = (float) Math.sin(lat1);
            float r0 = (float) Math.cos(lat0);
            float r1 = (float) Math.cos(lat1);

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= slices; j++) {
                double lng = 2.0 * Math.PI * (double) j / slices;
                float xCos = (float) Math.cos(lng);
                float zSin = (float) Math.sin(lng);

                // Vertex on the lower stack edge
                GL11.glNormal3f(xCos * r0, y0, zSin * r0);
                GL11.glVertex3f(radius * xCos * r0, radius * y0, radius * zSin * r0);

                // Vertex on the upper stack edge
                GL11.glNormal3f(xCos * r1, y1, zSin * r1);
                GL11.glVertex3f(radius * xCos * r1, radius * y1, radius * zSin * r1);
            }
            GL11.glEnd();
        }
    }

    public boolean shouldClose() {
        return shouldClose;
    }

    public void cleanup() {
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}
