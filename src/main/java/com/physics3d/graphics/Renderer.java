package com.physics3d.graphics;

import com.physics3d.model.CelestialBody;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Handles rendering of the 3D scene using OpenGL
 */
public class Renderer {
    private long window;
    private int width;
    private int height;
    private boolean shouldClose;
    
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
        
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);
    }
    
    private void initOpenGL() {
        GL.createCapabilities();
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GL11.glHint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);
    }
    
    public void render(List<CelestialBody> bodies) {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        
        // Setup projection matrix
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(-3e11f, 3e11f, -3e11f, 3e11f, -1e12f, 1e12f);
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        
        // Draw bodies as points
        GL11.glPointSize(5.0f);
        GL11.glBegin(GL11.GL_POINTS);
        
        for (CelestialBody body : bodies) {
            // Color based on body name
            if (body.getName().equals("Sun")) {
                GL11.glColor3f(1.0f, 1.0f, 0.0f);  // Yellow
            } else if (body.getName().equals("Earth")) {
                GL11.glColor3f(0.0f, 0.5f, 1.0f);  // Blue
            } else if (body.getName().equals("Mars")) {
                GL11.glColor3f(1.0f, 0.5f, 0.0f);  // Orange
            } else {
                GL11.glColor3f(1.0f, 1.0f, 1.0f);  // White
            }
            
            var pos = body.getPosition();
            GL11.glVertex3f(pos.x, pos.y, pos.z);
        }
        
        GL11.glEnd();
        
        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();
    }
    
    public boolean shouldClose() {
        return shouldClose;
    }
    
    public void cleanup() {
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}