package com.classroomgrouping.grouping.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public class GroupingInvitationsController {
    private final GroupingInvitations invitations;
    public GroupingInvitationsController(GroupingInvitations invitations){this.invitations=invitations;}
    @GetMapping("/api/courses/{courseId}/invitations")
    public GroupingInvitations.InvitationPage history(Principal principal,@PathVariable UUID courseId,
            @RequestParam(required=false) String cursor,@RequestParam(required=false) Integer limit){
        return invitations.history(principal,courseId,cursor,limit);
    }
    @PostMapping("/api/courses/{courseId}/groups/{groupId}/invitations")
    public GroupingInvitations.InvitationPage invite(Principal principal,@PathVariable UUID courseId,
            @PathVariable UUID groupId,@RequestBody Invitation request){
        return invitations.invite(principal,courseId,groupId,uuid(request.recipientId(),"recipientId"),key(request.commandKey()));
    }
    @PostMapping("/api/courses/{courseId}/invitations/{attemptId}/decision")
    public GroupingInvitations.InvitationPage decide(Principal principal,@PathVariable UUID courseId,
            @PathVariable UUID attemptId,@RequestBody Decision request){
        if(request.decision()==null||!request.decision().isString())throw new GroupingValidationException("decision");
        return invitations.decide(principal,courseId,attemptId,key(request.commandKey()),request.decision().asString());
    }
    private UUID key(JsonNode node){return uuid(node,"commandKey");}
    private UUID uuid(JsonNode node,String field){
        try{if(node==null||!node.isString())throw new IllegalArgumentException();String text=node.asString();UUID key=UUID.fromString(text);if(!key.toString().equalsIgnoreCase(text))throw new IllegalArgumentException();return key;}
        catch(IllegalArgumentException invalid){throw new GroupingValidationException(field);}
    }
    public record Invitation(JsonNode commandKey,JsonNode recipientId){
        @JsonAnySetter public void rejectUnknown(String field,JsonNode value){throw new GroupingValidationException("fields");}
        @Override public String toString(){return "Invitation[redacted]";}
    }
    public record Decision(JsonNode commandKey,JsonNode decision){
        @JsonAnySetter public void rejectUnknown(String field,JsonNode value){throw new GroupingValidationException("fields");}
        @Override public String toString(){return "Decision[redacted]";}
    }
}
