package org.eclipse.edc.odins.extension.xusercontext;

/**
 * Holder ThreadLocal para mantener el contexto de usuario durante 
 * toda la ejecución de una petición y sus llamadas internas.
 */
public class UserContextHolder {
    
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    
    /**
     * Establece el contexto de usuario para el hilo actual.
     */
    public static void setCurrentUser(String user) {
        if (user != null && !user.trim().isEmpty()) {
            CURRENT_USER.set(user.trim());
        }
    }
    
    /**
     * Obtiene el contexto de usuario del hilo actual.
     */
    public static String getCurrentUser() {
        return CURRENT_USER.get();
    }
    
    /**
     * Limpia el contexto de usuario del hilo actual.
     * IMPORTANTE: Siempre llamar para evitar memory leaks.
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
    
    /**
     * Verifica si hay un contexto de usuario establecido.
     */
    public static boolean hasCurrentUser() {
        return CURRENT_USER.get() != null;
    }
}