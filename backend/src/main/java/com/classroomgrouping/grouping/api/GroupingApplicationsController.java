package com.classroomgrouping.grouping.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public class GroupingApplicationsController {
    private final GroupingApplications applications;
    public GroupingApplicationsController(GroupingApplications applications){this.applications=applications;}
    @GetMapping("/api/courses/{courseId}/applications")
    public GroupingApplications.ApplicationPage history(Principal principal,@PathVariable UUID courseId,
            @RequestParam(required=false) String cursor,@RequestParam(required=false) Integer limit){
        return applications.history(principal,courseId,cursor,limit);
    }
    @PostMapping("/api/courses/{courseId}/groups/{groupId}/applications")
    public GroupingApplications.ApplicationPage apply(Principal principal,@PathVariable UUID courseId,
            @PathVariable UUID groupId,@RequestBody Apply request){
        return applications.apply(principal,courseId,groupId,key(request.commandKey()));
    }
    @PostMapping("/api/courses/{courseId}/applications/{attemptId}/decision")
    public GroupingApplications.ApplicationPage decide(Principal principal,@PathVariable UUID courseId,
            @PathVariable UUID attemptId,@RequestBody Decision request){
        if(request.decision()==null||!request.decision().isString())throw new GroupingValidationException("decision");
        return applications.decide(principal,courseId,attemptId,key(request.commandKey()),request.decision().asString());
    }
    private UUID key(JsonNode node){
        try{if(node==null||!node.isString())throw new IllegalArgumentException();String text=node.asString();UUID key=UUID.fromString(text);if(!key.toString().equalsIgnoreCase(text))throw new IllegalArgumentException();return key;}
        catch(IllegalArgumentException invalid){throw new GroupingValidationException("commandKey");}
    }
    public record Apply(JsonNode commandKey){
        @JsonAnySetter public void rejectUnknown(String field,JsonNode value){throw new GroupingValidationException("fields");}
        @Override public String toString(){return "Apply[redacted]";}
    }
    public record Decision(JsonNode commandKey,JsonNode decision){
        @JsonAnySetter public void rejectUnknown(String field,JsonNode value){throw new GroupingValidationException("fields");}
        @Override public String toString(){return "Decision[redacted]";}
    }
}
