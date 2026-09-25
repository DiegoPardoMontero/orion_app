package co.orion.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * Las sesiones en Postgres (V68), y qué pasa cuando una guardada ya no se puede leer.
 *
 * <p>Una sesión guardada es el contexto de seguridad serializado con Java. Si un despliegue cambia
 * de forma incompatible alguna clase que va dentro —una versión nueva de Spring Security cambia su
 * {@code serialVersionUID}—, leerla lanza una excepción, y Spring Session la dejaría subir: cada
 * persona con la sesión abierta recibiría un 500 en cada petición hasta borrar sus cookies. Aquí un
 * atributo ilegible se lee como vacío: la persona vuelve a entrar —lo mismo que pasaba antes con
 * cada despliegue— y nada más.
 */
@Configuration(proxyBeanMethods = false)
public class SesionesEnLaBase {

    private static final Logger log = LoggerFactory.getLogger(SesionesEnLaBase.class);

    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sesionIlegibleEsSesionVacia() {
        return repositorio -> repositorio.setConversionService(conversiones(SesionesEnLaBase.class.getClassLoader()));
    }

    /** Las mismas dos conversiones que trae Spring Session, salvo que leer nunca lanza. */
    static GenericConversionService conversiones(ClassLoader classLoader) {
        GenericConversionService conversiones = new GenericConversionService();
        conversiones.addConverter(Object.class, byte[].class, new SerializingConverter());
        DeserializingConverter leer = new DeserializingConverter(classLoader);
        conversiones.addConverter(byte[].class, Object.class, bytes -> {
            try {
                return leer.convert(bytes);
            } catch (RuntimeException ex) {
                log.warn("Un atributo de sesión guardado no se pudo leer y se descarta: {}", ex.getMessage());
                return null;
            }
        });
        return conversiones;
    }
}
