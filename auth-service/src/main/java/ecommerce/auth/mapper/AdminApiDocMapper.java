package ecommerce.auth.mapper;

import ecommerce.auth.entity.AdminApiDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 接口文档 Mapper */
@Mapper
public interface AdminApiDocMapper {

    List<AdminApiDoc> listDocs(@Param("service") String service, @Param("keyword") String keyword);

    AdminApiDoc findById(@Param("id") Long id);

    int upsert(AdminApiDoc doc);

    int updateHuman(@Param("id") Long id,
                    @Param("apiName") String apiName,
                    @Param("isPublic") Integer isPublic,
                    @Param("responseExample") String responseExample,
                    @Param("errorCodes") String errorCodes);

    List<String> listServices();

    List<AdminApiDoc> listKeys();

    int markMissingByService(@Param("service") String service);

    int countMissing();
}
