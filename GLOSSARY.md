# Classroom Grouping

The language used to describe courses and student team formation in the Classroom Grouping System.

## Language

**Course**:
A teaching context with assigned teachers and enrolled students. A course may have zero or one grouping activity in V1.

**Grouping activity**:
A course-specific exercise in which students form groups under teacher-configured rules.
_Avoid_: Course (when referring to the grouping exercise rather than the teaching context)

**Activity participant**:
A student enrolled in the course when its grouping activity is created, automatically included in that activity's fixed participant list. Later enrollment changes do not automatically add or remove activity participants in V1.

**Grouping cutoff**:
The current activity deadline at which all student grouping actions stop, including application and invitation responses. This cutoff is distinct from system assignment and final-result availability.

**Activity instructions**:
The teacher-provided task description and guidance for a grouping activity, which remain editable by its assigned teacher after grouping closes. They are distinct from the fixed group-capacity layout, grouping deadline, and final roster.
_Also called_: Activity description

**Group**:
A system-created student team with a predetermined capacity within a grouping activity.
_Avoid_: Course, grouping activity (when referring to the student team)

**Group name**:
The fixed activity-local label assigned to a group in capacity-list order, such as Group 1 or Group 2. V1 provides no custom naming or renaming.

**Target group size**:
The intended normal number of students in a group, allowing remainder adjustments for the class. For example, 9 students with target 2 may form groups of 2, 2, 2, 3; the target is not a strict exact size or final-group maximum and does not generate the manually entered layout.
_Avoid_: Exact group size, maximum group size (when referring to this target)

**Group capacity**:
The predetermined maximum number of members in a particular system-created group, expressed as a whole number of at least 2 in V1. It is distinct from the normal target group size.

**Capacity layout**:
The teacher's manually entered list of predetermined group capacities for a grouping activity, with one group per entry; its total must equal the activity's eligible student count. For example, 2, 2, 2, 3 provides four groups for nine eligible students.

**Membership**:
A student's established belonging to a group within a grouping activity. A student has at most one such membership at a time in that activity; a pending application or invitation is not a membership.

**Group manager**:
The initial student who deliberately joins an empty system-created group and becomes its member. This student alone may decide its applications and send invitations, without transfer or delegation in V1; groups first populated by post-deadline system assignment have no manager.
_Avoid_: Group creator (the system creates groups)

**Ordinary group member**:
A student who belongs to a group and is not its group manager.

**Member removal**:
Ending an existing student's membership by an action taken by someone else. Group managers cannot perform it in V1; it is distinct from rejecting a pending application and from a member choosing to leave.

**Grouped student**:
A student with a group membership in the grouping activity being discussed.

**Ungrouped student**:
A student with no group membership in the grouping activity being discussed.

**Join application**:
An ungrouped student's request to join an occupied group with a manager and available capacity, requiring that manager's approval; the student may apply to different groups with at most one pending application per target group. A pending application is neither membership nor a reserved group place, and approval can establish membership only when current joining conditions permit it.
_Avoid_: Membership (when referring to a pending request)

**Pending application**:
A join application awaiting its outcome, without establishing membership or reserving a group place. Its applicant cannot manually withdraw it in V1; automatic cancellation upon joining still applies.

**Pending request**:
A join application or group invitation awaiting its outcome, without membership or a reserved group place. One application and one invitation may be pending for the same student and group; successful joining cancels the still-pending counterpart.

**Request history**:
The completed application and invitation attempts, with their outcomes and cancellation reasons, retained for their two authorized parties to read. A later attempt or the grouping cutoff does not erase that history or make it a current membership record.

**Reapplication**:
A subsequent request by a student to join the same group after an earlier application has ended. V1 permits it after rejection, subject to current eligibility and at most one pending application for that student and group; every new attempt still requires current eligibility.

**Group invitation**:
An offer from a group's manager to an ungrouped classmate to join the group, which the recipient may accept or reject. Invitations from different groups may await a response simultaneously, with at most one per group and recipient; they are neither membership nor reserved group places.
_Avoid_: Join application (when referring to an offer sent by the group rather than a request from an applicant)

**Pending invitation**:
A group invitation awaiting its outcome, without establishing membership or reserving a place. The sending group manager cannot manually cancel it in V1; recipient responses and automatic cancellation upon joining remain subject to current rules.

**Reinvitation**:
A subsequent invitation from a group's manager to the same student after an earlier invitation has ended. V1 permits it after rejection, subject to current eligibility and at most one pending invitation for the group and recipient; every new attempt still requires current eligibility.

**Expired request**:
A join application or group invitation that was still pending when the activity's current deadline arrived and can no longer receive a response. This outcome is distinct from a person's rejection and from cancellation because the student joined a group.

**About me**:
A student's optional, self-written plain-text introduction for a particular course, visible to classmates alongside their name and institutional Student ID. It may be empty, contains at most 500 characters, and remains editable by its student and readable by classmates after the grouping deadline.

**Allocation plan**:
The chosen mapping of remaining ungrouped activity participants to existing group places for deadline completion. V1 retains the original random plan through retries rather than choosing different placements.

**System assignment**:
The system's placement of remaining ungrouped activity participants into existing empty group places after the deadline. V1 uses shuffled student ordering without considering prior applications/invitations, while preserving existing memberships and capacities.

**Final grouping roster**:
The completed grouping result finalized by the system after the deadline, with every eligible student belonging to exactly one group. At successful system finalization, the assigned teacher may view the full roster and each participant may view their own final group and permitted member information.

**Student ID**:
The institutional student number used to identify a student.
_Also called_: Student number

**Student name**:
The name recorded for a student and used to identify them in permitted course peer profiles and the teacher's final roster.

**Final-roster export**:
A CSV download of the finalized activity roster available to its assigned teacher. It is distinct from a student's own final-group view.

**Full group**:
A group whose current membership count equals its fixed capacity, with no remaining places. V1 rejects new applications and invitations to it; when its last place is filled, other students' requests still pending after the joining student's cleanup are system-canceled with reason "Group full".

**System-canceled request**:
A pending join application or group invitation closed automatically because its student joined a group in the same activity or its target group filled its last place. Its reason distinguishes joining from "Group full"; neither is a person's rejection or deadline expiry.
_Avoid_: Rejected request (when closure resulted from joining a group)

**Rejected request**:
A join application declined by the group manager, or a group invitation declined by its recipient. Rejection affects only that request, leaving a pending counterpart unchanged and imposing no standing ban on a later application or invitation.
_Avoid_: System-canceled request (when a person declined the request)

**Voluntary group departure**:
A member's choice to end their own group membership. It is temporarily excluded from V1 for both ordinary members and managers, and prioritized for V2 with detailed rules still undecided.
