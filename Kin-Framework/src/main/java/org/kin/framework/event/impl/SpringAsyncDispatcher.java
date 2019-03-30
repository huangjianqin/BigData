package org.kin.framework.event.impl;

import org.kin.framework.event.Event;
import org.kin.framework.event.EventHandler;
import org.kin.framework.event.HandleEvent;
import org.kin.framework.utils.ExceptionUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Created by huangjianqin on 2019/3/1.
 */
@Component
public class SpringAsyncDispatcher extends AsyncDispatcher implements InitializingBean, ApplicationContextAware {
    private static SpringAsyncDispatcher defalut;

    private ApplicationContext context;

    //setter && getter
    public static SpringAsyncDispatcher instance() {
        return defalut;
    }

    @Autowired
    public void setDefalut(SpringAsyncDispatcher defalut) {
        SpringAsyncDispatcher.defalut = defalut;
    }

    //------------------------------------------------------------------------------------------------------------------

    /**
     * 识别@HandleEvent注解的类 or 方法, 并自动注册
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, Object> beans = context.getBeansWithAnnotation(HandleEvent.class);
        for (Object bean : beans.values()) {
            Class claxx = bean.getClass();
            if (claxx.isAnnotationPresent(HandleEvent.class)) {
                HandleEvent handleEvent = (HandleEvent) claxx.getAnnotation(HandleEvent.class);
                Class eventType = handleEvent.type();
                if (eventType.isEnum()) {
                    //注解在类定义
                    if (claxx.isAssignableFrom(EventHandler.class)) {
                        EventHandler eventHandler = (EventHandler) bean;
                        register(eventType, eventHandler);
                    } else {
                        //在所有  public  方法中寻找一个匹配的方法作为事件处理方法
                        for (Method method : claxx.getMethods()) {
                            Class[] paramClasses = method.getParameterTypes();
                            if (paramClasses.length == 1) {
                                Class paramClass = paramClasses[0];
                                if (paramClass.isAssignableFrom(Event.class) && checkEventClass(eventType, paramClass)) {
                                    //满足条件
                                    register(eventType, new MethodAnnotationEventHandler(bean, method));
                                }
                            }
                        }
                    }
                } else {
                    throw new IllegalStateException("事件类型必须是枚举");
                }
            } else {
                //注解在方法
                //在所有  public & 有注解的  方法中寻找一个匹配的方法作为事件处理方法
                for (Method method : claxx.getMethods()) {
                    if (method.isAnnotationPresent(HandleEvent.class)) {
                        HandleEvent handleEvent = (HandleEvent) method.getAnnotation(HandleEvent.class);
                        Class eventType = handleEvent.type();
                        Class[] paramClasses = method.getParameterTypes();
                        if (paramClasses.length == 1) {
                            Class paramClass = paramClasses[0];
                            if (paramClass.isAssignableFrom(Event.class) && checkEventClass(eventType, paramClass)) {
                                //满足条件
                                register(eventType, new MethodAnnotationEventHandler(bean, method));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    //------------------------------------------------------------------------------------------------------------------
    class MethodAnnotationEventHandler implements EventHandler<Event> {
        private Object invoker;
        private Method method;

        public MethodAnnotationEventHandler(Object invoker, Method method) {
            this.invoker = invoker;
            this.method = method;
        }

        @Override
        public void handle(Event event) {
            method.setAccessible(true);
            try {
                method.invoke(invoker, event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                ExceptionUtils.log(e);
            } finally {
                method.setAccessible(false);
            }
        }
    }
}