package org.kin.hbase.core.utils;

import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.util.Bytes;
import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.hbase.core.annotation.Column;
import org.kin.hbase.core.annotation.HBaseEntity;
import org.kin.hbase.core.annotation.RowKey;
import org.kin.hbase.core.domain.HBaseConstants;
import org.kin.hbase.core.domain.QueryInfo;
import org.kin.hbase.core.exception.HBaseEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by huangjianqin on 2018/5/25.
 */
public class HBaseUtils {
    private static final Logger log = LoggerFactory.getLogger(HBaseConstants.HBASE_LOGGER);

    public static <T> List<Put> convert2Puts(T... entities) {
        return convert2Puts(Arrays.asList(entities));
    }

    /**
     * 将@HBaseEntity标识的类解析成Put实例, 不支持继承
     */
    public static <T> List<Put> convert2Puts(Collection<T> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Put> puts = new ArrayList<>();

        for (T entity : entities) {
            //有@HBaseEntity注解才解析
            if (entity.getClass().isAnnotationPresent(HBaseEntity.class)) {
                //如果是HBaseEntity实现类,需先序列化
                if (entity instanceof org.kin.hbase.core.entity.HBaseEntity) {
                    ((org.kin.hbase.core.entity.HBaseEntity) entity).serialize();
                }

                Put put = null;

                Field[] fields = entity.getClass().getDeclaredFields();
                //先找出row key的成员域
                for (Field f : fields) {
                    RowKey rowkey = f.getAnnotation(RowKey.class);
                    if (rowkey != null) {
                        byte[] rowKeyBytes = getFieldValue(entity, f);
                        assert rowKeyBytes != null;
                        put = new Put(rowKeyBytes);

                        //row key 只有一个
                        break;
                    }
                }

                //再找出qualifier的成员域
                for (Field f : fields) {
                    Column hbaseColumn = f.getAnnotation(Column.class);
                    if (hbaseColumn != null) {
                        byte[] value = getFieldValue(entity, f);
                        if (value != null) {
                            String vStr = Bytes.toString(value);
                            if (StringUtils.isNotBlank(vStr)) {
                                String family = hbaseColumn.family();
                                String qualifier = hbaseColumn.qualifier();
                                if (StringUtils.isBlank(qualifier)) {
                                    qualifier = f.getName();
                                }
                                assert put != null;
                                put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), value);
                            }
                        }
                    }
                }
                puts.add(put);
            } else {
                throw new HBaseEntityException("hbase entity must be annotated with @HBaseEntity");
            }
        }
        return puts;
    }

    /**
     * 如果还需要其他的类型请自己做扩展
     */
    private static byte[] getFieldValue(Object object, Field field) {
        try {
            Method m = ClassUtils.getterMethod(object.getClass(), field);
            if (String.class.equals(field)) {
                String val;
                if (m != null) {
                    // 调用getter方法获取属性值
                    val = (String) m.invoke(object);
                } else {
                    val = field.get(object).toString();
                }

                return Bytes.toBytes(val);
            }

            if (Character.class.equals(field) || Character.TYPE.equals(field)) {
                char val;
                if (m != null) {
                    // 调用getter方法获取属性值
                    val = (char) m.invoke(object);
                } else {
                    val = field.getChar(object);
                }

                return Bytes.toBytes(val);
            }

            if (Integer.class.equals(field) || Integer.TYPE.equals(field)) {
                int val;
                if (m != null) {
                    val = (int) m.invoke(object);
                } else {
                    val = field.getInt(object);
                }

                return Bytes.toBytes(val);
            }

            if (Double.class.equals(field) || Double.TYPE.equals(field)) {
                double val;
                if (m != null) {
                    val = (double) m.invoke(object);
                } else {
                    val = field.getDouble(object);
                }

                return Bytes.toBytes(val);
            }

            if (Long.class.equals(field) || Long.TYPE.equals(field)) {
                long val;
                if (m != null) {
                    val = (long) m.invoke(object);
                } else {
                    val = field.getLong(object);
                }

                return Bytes.toBytes(val);
            }

            if (Byte.class.equals(field) || Byte.TYPE.equals(field)) {
                byte val;
                if (m != null) {
                    val = (byte) m.invoke(object);
                } else {
                    val = field.getByte(object);
                }

                return Bytes.toBytes(val);
            }

            if (Short.class.equals(field) || Short.TYPE.equals(field)) {
                short val;
                if (m != null) {
                    val = (byte) m.invoke(object);
                } else {
                    val = field.getShort(object);
                }

                return Bytes.toBytes(val);
            }

            if (Float.class.equals(field) || Float.TYPE.equals(field)) {
                float val;
                if (m != null) {
                    val = (float) m.invoke(object);
                } else {
                    val = field.getFloat(object);
                }

                return Bytes.toBytes(val);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        return null;
    }

    /**
     * 如果还需要其他的类型请自己做扩展
     */
    private static <H, T> void setFieldValue(H instance, Field field, T value) {
        if (value == null) {
            return;
        }
        try {
            Method m = ClassUtils.setterMethod(instance.getClass(), field);
            if (m != null) {
                m.invoke(instance, value);
            } else {
                field.set(instance, value);
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 解析注解，注入数据
     */
    public static <T> T convert2HBaseEntity(Class<T> clazz, Result result) {
        if (clazz == null || result == null) {
            return null;
        }

        Field[] fields = clazz.getDeclaredFields();
        T instance = null;
        try {
            //检查是否有@HBaseEntity注解
            if (clazz.isAnnotationPresent(HBaseEntity.class)) {
                instance = clazz.newInstance();
                for (Field field : fields) {
                    field.setAccessible(true);
                    //设置RowKey @RowKey
                    if (field.isAnnotationPresent(RowKey.class)) {
                        setFieldValue(instance, field, ClassUtils.convertBytes2PrimitiveObj(field.getType(), result.getRow()));
                    } else {
                        //设置列值 @Column
                        if (field.isAnnotationPresent(Column.class)) {
                            Column column = field.getAnnotation(Column.class);

                            String family = column.family();
                            String qualifier = column.qualifier();

                            if (StringUtils.isBlank(qualifier)) {
                                qualifier = field.getName();
                            }

                            if (StringUtils.isBlank(family)) {
                                throw new IllegalArgumentException("@Column 's qualifier family must be not blank");
                            }

                            setFieldValue(instance, field, ClassUtils.convertBytes2PrimitiveObj(field.getType(), result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier))));
                        }
                    }
                }

                //如果是HBaseEntity实现类,需反序列化
                if (instance instanceof org.kin.hbase.core.entity.HBaseEntity) {
                    ((org.kin.hbase.core.entity.HBaseEntity) instance).deserialize();
                }
            }
        } catch (InstantiationException e) {
            log.error(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            log.error(e.getMessage(), e);
        } finally {
            for (Field field : fields) {
                field.setAccessible(false);
            }
        }

        return instance;
    }

    /**
     * 设置Get/Scan filter过滤 & column信息
     */
    public static void setColumnAndFilter(OperationWithAttributes operation, List<QueryInfo> queryInfos, List<Filter> filters) {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        if (queryInfos != null && queryInfos.size() > 0) {
            for (QueryInfo queryInfo : queryInfos) {
                String family = queryInfo.getFamily();
                String qualifier = queryInfo.getQualifier();

                if (StringUtils.isBlank(qualifier)) {
                    if (operation instanceof Scan) {
                        ((Scan) operation).addFamily(Bytes.toBytes(family));
                    } else if (operation instanceof Get) {
                        ((Get) operation).addFamily(Bytes.toBytes(family));
                    }
                } else {
                    if (operation instanceof Scan) {
                        ((Scan) operation).addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier));
                    } else if (operation instanceof Get) {
                        ((Get) operation).addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier));
                    }
                }
            }
        }

        if (filters != null && filters.size() > 0) {
            for (Filter filter : filters) {
                filterList.addFilter(filter);
            }

            if (operation instanceof Scan) {
                ((Scan) operation).setFilter(filterList);
            } else if (operation instanceof Get) {
                ((Get) operation).setFilter(filterList);
            }
        }
    }

    public static <T> byte[] getRowKeyBytes(T entity) {
        if (entity.getClass().isAnnotationPresent(HBaseEntity.class)) {
            Field[] fields = entity.getClass().getDeclaredFields();
            for (Field f : fields) {
                RowKey rowkey = f.getAnnotation(RowKey.class);
                if (rowkey != null) {
                    return getFieldValue(entity, f);
                }
            }
        }

        return null;
    }

}
