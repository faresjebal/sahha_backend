package com.sahha.communication.attachment;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.repository.*;

@Service
public class AttachmentAuthority {
    private final ConversationThreadRepository threads;
    private final ConversationParticipantRepository participants;
    private final OrganisationCollaborationClient directory;
    private final JdbcTemplate jdbc;
    private final AttachmentAccessAudit audit;
    public AttachmentAuthority(ConversationThreadRepository threads, ConversationParticipantRepository participants,
            OrganisationCollaborationClient directory, JdbcTemplate jdbc, AttachmentAccessAudit audit) {
        this.threads=threads; this.participants=participants; this.directory=directory; this.jdbc=jdbc; this.audit=audit;
    }
    public AttachmentContext require(UUID conversation, UUID org, UUID actor, String token, UUID file) {
        try {
            threads.findParticipantThread(conversation, org, actor).orElseThrow(ConversationNotFoundException::new);
            var members=participants.findAllByConversationIdAndOrganisationIdAndActiveTrueOrderByJoinedAt(conversation, org);
            if(members.size()!=2 || members.stream().noneMatch(value->actor.equals(value.getUserId())))
                throw new ConversationNotFoundException();
            for(var member:members) {
                var current=directory.resolve(org, member.getUserId(), token);
                if(!member.getMembershipId().equals(current.membershipId())
                        || !org.equals(current.organisationId()) || !member.getUserId().equals(current.userId()))
                    throw new ConversationNotFoundException();
            }
            AttachmentContext result;
            if(file==null) result=new AttachmentContext(conversation,org,actor,null,null,null,null);
            else result=jdbc.query("""
                    SELECT m.id, m.message_request_id, m.sender_user_id FROM conversation_message_attachment a
                    JOIN conversation_message m ON m.id=a.message_id AND m.organisation_id=a.organisation_id
                    WHERE a.file_id=? AND a.organisation_id=? AND m.conversation_id=?
                    """,(rs,row)->new AttachmentContext(conversation,org,actor,file,rs.getObject("id",UUID.class),
                        rs.getObject("message_request_id",UUID.class),rs.getObject("sender_user_id",UUID.class)),
                    file,org,conversation).stream().findFirst().orElseThrow(ConversationNotFoundException::new);
            audit.record(org,conversation,actor,true);
            return result;
        } catch(RuntimeException failure) {
            audit.record(org,conversation,actor,false);
            throw failure;
        }
    }
}
