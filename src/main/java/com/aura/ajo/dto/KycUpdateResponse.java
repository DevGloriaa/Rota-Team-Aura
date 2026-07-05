package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycUpdateResponse {

    private MemberResponse member;

    /** True only when the group is still FORMING — rotation isn't locked yet, so the new
     *  score can shift this member's position at activation. Always false for ACTIVE groups,
     *  whose rotation order is immutable once locked. */
    private boolean rotationAffected;
}
