package neatlogic.module.process.api.commenttemplate;

import neatlogic.framework.auth.core.AuthAction;
import neatlogic.framework.common.constvalue.ApiParamType;
import neatlogic.framework.process.auth.PROCESS_BASE;
import neatlogic.framework.process.dao.mapper.ProcessCommentTemplateMapper;
import neatlogic.framework.process.dto.ProcessCommentTemplateAuthVo;
import neatlogic.framework.process.dto.ProcessCommentTemplateVo;
import neatlogic.framework.process.exception.commenttemplate.ProcessCommentTemplateNotFoundException;
import neatlogic.framework.restful.constvalue.OperationTypeEnum;
import neatlogic.framework.restful.annotation.Input;
import neatlogic.framework.restful.annotation.OperationType;
import neatlogic.framework.restful.annotation.Output;
import neatlogic.framework.restful.annotation.Param;
import neatlogic.framework.restful.core.privateapi.PrivateApiComponentBase;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@AuthAction(action = PROCESS_BASE.class)
@OperationType(type = OperationTypeEnum.SEARCH)
public class ProcessCommentTemplateGetApi extends PrivateApiComponentBase {

    @Autowired
    private ProcessCommentTemplateMapper commentTemplateMapper;

    @Override
    public String getToken() {
        return "process/comment/template/get";
    }

    @Override
    public String getName() {
        return "获取回复模版";
    }

    @Override
    public String getConfig() {
        return null;
    }

    @Input({@Param(name = "id", type = ApiParamType.LONG, desc = "回复模版ID")})
    @Output({@Param(name = "template", explode = ProcessCommentTemplateVo.class, desc = "回复模版")})
    @Override
    public Object myDoService(JSONObject jsonObj) throws Exception {
        JSONObject returnObj = new JSONObject();
        Long id = jsonObj.getLong("id");
        ProcessCommentTemplateVo vo = commentTemplateMapper.getTemplateById(id);
        if(vo == null){
            throw new ProcessCommentTemplateNotFoundException(id);
        }
        if (ProcessCommentTemplateVo.TempalteType.SYSTEM.getValue().equals(vo.getType())) {
            List<String> authList = new ArrayList<>();
            List<ProcessCommentTemplateAuthVo> authVoList = commentTemplateMapper.getProcessCommentTemplateAuthListByCommentTemplateId(id);
            for (ProcessCommentTemplateAuthVo authVo : authVoList) {
                authList.add(authVo.getType() + "#" + authVo.getUuid());
            }
            vo.setAuthList(authList);
        }
        returnObj.put("template", vo);
        return returnObj;
    }
}
