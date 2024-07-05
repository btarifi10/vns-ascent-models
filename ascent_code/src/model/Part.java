/*
The copyrights of this software are owned by Duke University.
Please refer to the LICENSE and README.md files for licensing instructions.
The source code can be found on the following GitHub repository: https://github.com/wmglab-duke/ascent
*/

package model;

import com.comsol.model.*;
import com.comsol.model.physics.PhysicsFeature;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONObject;

@SuppressWarnings({ "path" })
class Part {

    public static void addPointCurrentSource(
        ModelWrapper mw,
        Model model,
        int index,
        String instanceLabel,
        String selLabel
    ) {
        String ribbon_pcsLabel = instanceLabel + " Current Source";
        String id = mw.im.next("pcs", ribbon_pcsLabel);
        PhysicsFeature pf = model
            .component("comp1")
            .physics("ec")
            .create(id, "PointCurrentSource", 0);

        JSONObject src_ribbon = new JSONObject();
        src_ribbon.put("name", instanceLabel);
        src_ribbon.put("pcs", id);
        src_ribbon.put("cuff_index", String.valueOf(index));
        mw.im.currentIDs.put(mw.im.present("pcs"), src_ribbon);

        pf.selection().named("geom1_" + mw.im.get(instanceLabel) + "_" + selLabel + "_pnt"); // SRC
        pf.set("Qjp", 0.000);
        pf.label(ribbon_pcsLabel);
    }

    /**
     * Create a defined part primitive. There is a finite number of choices, as seen below in the switch.
     * Fun fact: this method is nearly 1400 lines.
     * @param id the part primitive COMSOL id (unique) --> use mw.im.next in call (part)
     * @param pseudonym the global name for that part, as used in mw.im
     * @param mw the ModelWrapper to act upon
     * @return the local IdentifierManager for THIS PART PRIMITIVE --> save when called in ModelWrapper
     * @throws IllegalArgumentException if an invalid pseudonym is passed in --> there is no such primitive to create
     */

    public static IdentifierManager createEnvironmentPartPrimitive(
        String id,
        String pseudonym,
        ModelWrapper mw
    ) throws IllegalArgumentException {
        Model model = mw.getModel();

        // only used once per method, so ok to define outside the switch
        model.geom().create(id, "Part", 3);
        model.geom(id).label(pseudonym);
        model.geom(id).lengthUnit("um");

        // only used once per method, so ok to define outside the switch
        IdentifierManager im = new IdentifierManager();
        ModelParam mp = model.geom(id).inputParam();

        if ("Medium_Primitive".equals(pseudonym)) {
            mp.set("radius", "10 [mm]");
            mp.set("length", "100 [mm]");

            im.labels =
                new String[] {
                    "MEDIUM", //0
                };

            for (String cselMediumLabel : im.labels) {
                model
                    .geom(id)
                    .selection()
                    .create(im.next("csel", cselMediumLabel), "CumulativeSelection")
                    .label(cselMediumLabel);
            }

            String mediumLabel = "Medium";
            GeomFeature m = model.geom(id).create(im.next("cyl", mediumLabel), "Cylinder");
            m.label(mediumLabel);
            m.set("r", "radius");
            m.set("h", "length");
            m.set("contributeto", im.get("MEDIUM"));
        } else {
            throw new IllegalArgumentException(
                "No implementation for part primitive name: " + pseudonym
            );
        }
        return im;
    }

    /**
     */
    public static void createEnvironmentPartInstance(
        String instanceID,
        String instanceLabel,
        String pseudonym,
        ModelWrapper mw,
        JSONObject infoMedium
    ) throws IllegalArgumentException {
        Model model = mw.getModel();

        GeomFeature partInstance = model
            .component("comp1")
            .geom("geom1")
            .create(instanceID, "PartInstance");
        partInstance.label(instanceLabel);
        partInstance.set("part", mw.im.get(pseudonym));

        IdentifierManager myIM = mw.getPartPrimitiveIM(pseudonym);
        if (myIM == null) throw new IllegalArgumentException(
            "IdentfierManager not created for name: " + pseudonym
        );

        String[] myLabels = myIM.labels; // may be null, but that is ok if not used

        if ("Medium_Primitive".equals(pseudonym)) { // set instantiation parameters
            if ("DistalMedium".equals(instanceLabel)) {
                partInstance.setEntry("inputexpr", "radius", "r_distal");
                partInstance.setEntry("inputexpr", "length", "z_distal");
                partInstance.set(
                    "displ",
                    new String[] { "distal_shift_x", "distal_shift_y", "distal_shift_z" }
                );
            } else if ("ProximalMedium".equals(instanceLabel)) {
                partInstance.setEntry("inputexpr", "radius", "r_proximal");
                partInstance.setEntry("inputexpr", "length", "z_nerve");
            }

            // imports
            partInstance.set("selkeepnoncontr", false);
            partInstance.setEntry(
                "selkeepdom",
                instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                "on"
            ); // MEDIUM

            partInstance.setEntry(
                "selkeepbnd",
                instanceID + "_" + myIM.get(myLabels[0]) + ".bnd",
                "on"
            ); // MEDIUM

            if (infoMedium.getBoolean("distant_ground")) {
                // assign physics
                String groundLabel = "Ground";
                PhysicsFeature gnd = model
                    .component("comp1")
                    .physics("ec")
                    .create(mw.im.next("gnd", groundLabel), "Ground", 2);
                gnd.label(groundLabel);
                gnd
                    .selection()
                    .named(
                        "geom1_" + mw.im.get(instanceLabel) + "_" + myIM.get(myLabels[0]) + "_bnd"
                    );
            }
        } else {
            throw new IllegalArgumentException(
                "No implementation for part primitive name: " + pseudonym
            );
        }
    }

    public static IdentifierManager createCuffPartPrimitive(
        String id,
        String pseudonym,
        ModelWrapper mw
    ) throws IllegalArgumentException {
        Model model = mw.getModel();

        // only used once per method, so ok to define outside the switch
        model.geom().create(id, "Part", 3);
        model.geom(id).label(pseudonym);
        model.geom(id).lengthUnit("um");

        // only used once per method, so ok to define outside the switch
        IdentifierManager im = new IdentifierManager();
        ModelParam mp = model.geom(id).inputParam();

        // prepare yourself for 1400 lines of pure COMSOL goodness
        // a true behemoth of a switch
        switch (pseudonym) {
            case "TubeCuff_Primitive":
                mp.set("N_holes", "0");
                mp.set("Tube_theta", "340 [deg]");
                mp.set("Center", "10 [mm]");
                mp.set("R_in", "1 [mm]");
                mp.set("R_out", "2 [mm]");
                mp.set("Tube_L", "5 [mm]");
                mp.set("Rot_def", "0 [deg]");
                mp.set("D_hole", "0.3 [mm]");
                mp.set("Buffer_hole", "0.1 [mm]");
                mp.set("L_holecenter_cuffseam", "0.3 [mm]");
                mp.set("Pitch_holecenter_holecenter", "0 [mm]");

                im.labels =
                    new String[] {
                        "INNER CUFF SURFACE", //0
                        "OUTER CUFF SURFACE",
                        "CUFF FINAL",
                        "CUFF wGAP PRE HOLES",
                        "CUFF PRE GAP",
                        "CUFF PRE GAP PRE HOLES", //5
                        "CUFF GAP CROSS SECTION",
                        "CUFF GAP",
                        "CUFF PRE HOLES",
                        "HOLE 1",
                        "HOLE 2", //10
                        "HOLES",
                    };

                for (String cselTCLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselTCLabel), "CumulativeSelection")
                        .label(cselTCLabel);
                }

                String micsLabel = "Make Inner Cuff Surface";
                GeomFeature inner_surf = model
                    .geom(id)
                    .create(im.next("cyl", micsLabel), "Cylinder");
                inner_surf.label(micsLabel);
                inner_surf.set("contributeto", im.get(im.labels[0]));
                inner_surf.set("pos", new String[] { "0", "0", "Center-(Tube_L/2)" });
                inner_surf.set("r", "R_in");
                inner_surf.set("h", "Tube_L");

                String mocsLabel = "Make Outer Cuff Surface";
                GeomFeature outer_surf = model
                    .geom(id)
                    .create(im.next("cyl", mocsLabel), "Cylinder");
                outer_surf.label(mocsLabel);
                outer_surf.set("contributeto", im.get("OUTER CUFF SURFACE"));
                outer_surf.set("pos", new String[] { "0", "0", "Center-(Tube_L/2)" });
                outer_surf.set("r", "R_out");
                outer_surf.set("h", "Tube_L");

                String ifgnhLabel = "If (No Gap AND No Holes)";
                GeomFeature if_gap_no_holes = model
                    .geom(id)
                    .create(im.next("if", ifgnhLabel), "If");
                if_gap_no_holes.label(ifgnhLabel);
                if_gap_no_holes.set("condition", "(Tube_theta>=359) && (N_holes==0)");

                String difrdwicsLabel = "Remove Domain Within Inner Cuff Surface";
                GeomFeature dif_remove_ics = model
                    .geom(id)
                    .create(im.next("dif", difrdwicsLabel), "Difference");
                dif_remove_ics.label(difrdwicsLabel);
                dif_remove_ics.set("contributeto", im.get("CUFF FINAL"));
                dif_remove_ics.selection("input").named(im.get("OUTER CUFF SURFACE"));
                dif_remove_ics.selection("input2").named(im.get("INNER CUFF SURFACE"));

                String elseifganhLabel = "If (Gap AND No Holes)";
                GeomFeature elseif_gap_noholes = model
                    .geom(id)
                    .create(im.next("elseif", elseifganhLabel), "ElseIf");
                elseif_gap_noholes.label(elseifganhLabel);
                elseif_gap_noholes.set("condition", "(Tube_theta<359) && (N_holes==0)");

                String difrmwics1Label = "Remove Domain Within Inner Cuff Surface 1";
                GeomFeature dif_remove_ics1 = model
                    .geom(id)
                    .create(im.next("dif", difrmwics1Label), "Difference");
                dif_remove_ics1.label(difrmwics1Label);
                dif_remove_ics1.set("contributeto", im.get("CUFF PRE GAP"));
                dif_remove_ics1.selection("input").named(im.get("OUTER CUFF SURFACE"));
                dif_remove_ics1.selection("input2").named(im.get("INNER CUFF SURFACE"));

                String wpmcgcsLabel = "Make Cuff Gap Cross Section";
                GeomFeature wp_make_cuffgapcx = model
                    .geom(id)
                    .create(im.next("wp", wpmcgcsLabel), "WorkPlane");
                wp_make_cuffgapcx.label(wpmcgcsLabel);
                wp_make_cuffgapcx.set("contributeto", im.get("CUFF GAP CROSS SECTION"));
                wp_make_cuffgapcx.set("quickplane", "xz");
                wp_make_cuffgapcx.set("unite", true);
                wp_make_cuffgapcx.geom().create("r1", "Rectangle");
                wp_make_cuffgapcx.geom().feature("r1").label("Cuff Gap Cross Section");
                wp_make_cuffgapcx
                    .geom()
                    .feature("r1")
                    .set("pos", new String[] { "R_in+((R_out-R_in)/2)", "Center" });
                wp_make_cuffgapcx.geom().feature("r1").set("base", "center");
                wp_make_cuffgapcx
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "R_out-R_in", "Tube_L" });

                String revmcgLabel = "Make Cuff Gap";
                GeomFeature rev_make_cuffgap = model
                    .geom(id)
                    .create(im.next("rev", revmcgLabel), "Revolve");
                rev_make_cuffgap.label(revmcgLabel);
                rev_make_cuffgap.set("contributeto", im.get("CUFF GAP"));
                rev_make_cuffgap.set("angle1", "Tube_theta");
                rev_make_cuffgap.selection("input").set(im.get("Make Cuff Gap Cross Section"));

                String difrcgLabel = "Remove Cuff Gap";
                GeomFeature dif_remove_cuffgap = model
                    .geom(id)
                    .create(im.next("dif", difrcgLabel), "Difference");
                dif_remove_cuffgap.label(difrcgLabel);
                dif_remove_cuffgap.set("contributeto", im.get("CUFF FINAL"));
                dif_remove_cuffgap.selection("input").named(im.get("CUFF PRE GAP"));
                dif_remove_cuffgap.selection("input2").named(im.get("CUFF GAP"));

                String rotdc1Label = "Rotate to Default Conformation 1";
                GeomFeature rot_default_conformation1 = model
                    .geom(id)
                    .create(im.next("rot", rotdc1Label), "Rotate");
                rot_default_conformation1.label(rotdc1Label);
                rot_default_conformation1.set("rot", "Rot_def");
                rot_default_conformation1.selection("input").named(im.get("CUFF FINAL"));

                String elifngnhLabel = "If (No Gap AND Holes)";
                GeomFeature elif_nogap_noholes = model
                    .geom(id)
                    .create(im.next("elseif", elifngnhLabel), "ElseIf");
                elif_nogap_noholes.label(elifngnhLabel);
                elif_nogap_noholes.set("condition", "(Tube_theta>=359) && (N_holes>0)");

                String difrdwics2 = "Remove Domain Within Inner Cuff Surface 2";
                GeomFeature dif_remove_domain_inner_cuff2 = model
                    .geom(id)
                    .create(im.next("dif", difrdwics2), "Difference");
                dif_remove_domain_inner_cuff2.label(difrdwics2);
                dif_remove_domain_inner_cuff2.set("contributeto", im.get("CUFF PRE HOLES"));
                dif_remove_domain_inner_cuff2
                    .selection("input")
                    .named(im.get("OUTER CUFF SURFACE"));
                dif_remove_domain_inner_cuff2
                    .selection("input2")
                    .named(im.get("INNER CUFF SURFACE"));

                String econmhsLabel = "Make Hole Shape";
                GeomFeature econ_make_holeshape = model
                    .geom(id)
                    .create(im.next("econ", econmhsLabel), "ECone");
                econ_make_holeshape.label(econmhsLabel);
                econ_make_holeshape.set("contributeto", im.get("HOLES"));
                econ_make_holeshape.set(
                    "pos",
                    new String[] {
                        "R_in-Buffer_hole/2",
                        "0",
                        "Center+Pitch_holecenter_holecenter/2",
                    }
                );
                econ_make_holeshape.set("axis", new int[] { 1, 0, 0 });
                econ_make_holeshape.set("semiaxes", new String[] { "D_hole/2", "D_hole/2" });
                econ_make_holeshape.set("h", "(R_out-R_in)+Buffer_hole");
                econ_make_holeshape.set("rat", "R_out/R_in");

                String rotphicLabel = "Position Hole in Cuff";
                GeomFeature rot_pos_hole = model
                    .geom(id)
                    .create(im.next("rot", rotphicLabel), "Rotate");
                rot_pos_hole.label(rotphicLabel);
                rot_pos_hole.set("rot", "(360*L_holecenter_cuffseam)/(pi*2*R_in)");
                rot_pos_hole.selection("input").named(im.get("HOLES"));

                String difmichLabel = "Make Inner Cuff Hole";
                GeomFeature dif_make_innercuff_hole = model
                    .geom(id)
                    .create(im.next("dif", difmichLabel), "Difference");
                dif_make_innercuff_hole.label(difmichLabel);
                dif_make_innercuff_hole.set("contributeto", im.get("CUFF FINAL"));
                dif_make_innercuff_hole.selection("input").named(im.get("CUFF PRE HOLES"));
                dif_make_innercuff_hole.selection("input2").named(im.get("HOLES"));

                String elifgahLabel = "If (Gap AND Holes)";
                GeomFeature elif_gap_and_holes = model
                    .geom(id)
                    .create(im.next("elseif", elifgahLabel), "ElseIf");
                elif_gap_and_holes.label(elifgahLabel);
                elif_gap_and_holes.set("condition", "(Tube_theta<359) && (N_holes>0)");

                String difrdwics3Label = "Remove Domain Within Inner Cuff Surface 3";
                GeomFeature dif_remove_domain_inner_cuff3 = model
                    .geom(id)
                    .create(im.next("dif", difrdwics3Label), "Difference");
                dif_remove_domain_inner_cuff3.label(difrdwics3Label);
                dif_remove_domain_inner_cuff3.set("contributeto", im.get("CUFF PRE GAP PRE HOLES"));
                dif_remove_domain_inner_cuff3
                    .selection("input")
                    .named(im.get("OUTER CUFF SURFACE"));
                dif_remove_domain_inner_cuff3
                    .selection("input2")
                    .named(im.get("INNER CUFF SURFACE"));

                String wpmcgcs1Label = "Make Cuff Gap Cross Section 1";
                GeomFeature wp_make_cuffgapcx1 = model
                    .geom(id)
                    .create(im.next("wp", wpmcgcs1Label), "WorkPlane");
                wp_make_cuffgapcx1.label(wpmcgcs1Label);
                wp_make_cuffgapcx1.set("contributeto", im.get("CUFF GAP CROSS SECTION"));
                wp_make_cuffgapcx1.set("quickplane", "xz");
                wp_make_cuffgapcx1.set("unite", true);
                wp_make_cuffgapcx1.geom().create("r1", "Rectangle");
                wp_make_cuffgapcx1.geom().feature("r1").label("Cuff Gap Cross Section");
                wp_make_cuffgapcx1
                    .geom()
                    .feature("r1")
                    .set("pos", new String[] { "R_in+((R_out-R_in)/2)", "Center" });
                wp_make_cuffgapcx1.geom().feature("r1").set("base", "center");
                wp_make_cuffgapcx1
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "R_out-R_in", "Tube_L" });

                String revmcg1Label = "Make Cuff Gap 1";
                GeomFeature rev_make_cuffgap1 = model
                    .geom(id)
                    .create(im.next("rev", revmcg1Label), "Revolve");
                rev_make_cuffgap1.label(revmcg1Label);
                rev_make_cuffgap1.set("contributeto", im.get("CUFF GAP"));
                rev_make_cuffgap1.set("angle1", "Tube_theta");
                rev_make_cuffgap1.selection("input").named(im.get("CUFF GAP CROSS SECTION"));

                String difrcg1Label = "Remove Cuff Gap 1";
                GeomFeature dif_remove_cuffgap1 = model
                    .geom(id)
                    .create(im.next("dif", difrcg1Label), "Difference");
                dif_remove_cuffgap1.label(difrcg1Label);
                dif_remove_cuffgap1.set("contributeto", im.get("CUFF wGAP PRE HOLES"));
                dif_remove_cuffgap1.selection("input").named(im.get("CUFF PRE GAP PRE HOLES"));
                dif_remove_cuffgap1.selection("input2").named(im.get("CUFF GAP"));

                String econmhs1Label = "Make Hole Shape 1";
                GeomFeature econ_makehole1 = model
                    .geom(id)
                    .create(im.next("econ", econmhs1Label), "ECone");
                econ_makehole1.label(econmhs1Label);
                econ_makehole1.set("contributeto", im.get("HOLES"));
                econ_makehole1.set(
                    "pos",
                    new String[] {
                        "R_in-Buffer_hole/2",
                        "0",
                        "Center+Pitch_holecenter_holecenter/2",
                    }
                );
                econ_makehole1.set("axis", new int[] { 1, 0, 0 });
                econ_makehole1.set("semiaxes", new String[] { "D_hole/2", "D_hole/2" });
                econ_makehole1.set("h", "(R_out-R_in)+Buffer_hole");
                econ_makehole1.set("rat", "R_out/R_in");

                String ifg2hLabel = "If (Gap AND 2 Holes)";
                GeomFeature if_gap2holes = model.geom(id).create(im.next("if", ifg2hLabel), "If");
                if_gap2holes.label(ifg2hLabel);
                if_gap2holes.set("condition", "N_holes==2");

                String econmhs2Label = "Make Hole Shape 2";
                GeomFeature econ_makehole2 = model
                    .geom(id)
                    .create(im.next("econ", econmhs2Label), "ECone");
                econ_makehole2.label(econmhs2Label);
                econ_makehole2.set("contributeto", im.get("HOLES"));
                econ_makehole2.set(
                    "pos",
                    new String[] {
                        "R_in-Buffer_hole/2",
                        "0",
                        "Center-Pitch_holecenter_holecenter/2",
                    }
                );
                econ_makehole2.set("axis", new int[] { 1, 0, 0 });
                econ_makehole2.set("semiaxes", new String[] { "D_hole/2", "D_hole/2" });
                econ_makehole2.set("h", "(R_out-R_in)+Buffer_hole");
                econ_makehole2.set("rat", "R_out/R_in");

                String endifg2hLabel = "End If (Gap AND 2 Holes)";
                GeomFeature endifg2h = model
                    .geom(id)
                    .create(im.next("endif", endifg2hLabel), "EndIf");
                endifg2h.label(endifg2hLabel);

                String rotphic1Label = "Position Hole in Cuff 1";
                GeomFeature rot_position_hole1 = model
                    .geom(id)
                    .create(im.next("rot", rotphic1Label), "Rotate");
                rot_position_hole1.label(rotphic1Label);
                rot_position_hole1.set("rot", "(360*L_holecenter_cuffseam)/(pi*2*R_in)");
                rot_position_hole1.selection("input").named(im.get("HOLES"));

                String difmich1Label = "Make Inner Cuff Hole 1";
                GeomFeature dif_make_hole1 = model
                    .geom(id)
                    .create(im.next("dif", difmich1Label), "Difference");
                dif_make_hole1.label(difmich1Label);
                dif_make_hole1.set("contributeto", im.get("CUFF FINAL"));
                dif_make_hole1.selection("input").named(im.get("CUFF wGAP PRE HOLES"));
                dif_make_hole1.selection("input2").named(im.get("HOLES"));

                String rotdcLabel = "Rotate to Default Conformation";
                GeomFeature rot_default_conformation = model
                    .geom(id)
                    .create(im.next("rot", rotdcLabel), "Rotate");
                rot_default_conformation.label(rotdcLabel);
                rot_default_conformation.set("rot", "Rot_def");
                rot_default_conformation.selection("input").named(im.get("CUFF FINAL"));

                String endifLabel = "End";
                GeomFeature endif = model.geom(id).create(im.next("endif", endifLabel), "EndIf");
                endif.label(endifLabel);

                model.geom(id).run();

                break;
            case "RibbonContact_Primitive":
                mp.set("Ribbon_thk", "0.1 [mm]");
                mp.set("Ribbon_z", "3 [mm]");
                mp.set("R_in", "1 [mm]");
                mp.set("Ribbon_recess", "0.1 [mm]");
                mp.set("Center", "10 [mm]");
                mp.set("Ribbon_theta", "100 [deg]");
                mp.set("Rot_def", "0 [deg]");

                im.labels =
                    new String[] {
                        "CONTACT CROSS SECTION", //0
                        "RECESS CROSS SECTION",
                        "SRC",
                        "CONTACT FINAL",
                        "RECESS FINAL",
                    };

                for (String cselRiCLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselRiCLabel), "CumulativeSelection")
                        .label(cselRiCLabel);
                }

                String wpccxLabel = "Contact Cross Section";
                GeomFeature wp_contact_cx = model
                    .geom(id)
                    .create(im.next("wp", wpccxLabel), "WorkPlane");
                wp_contact_cx.label(wpccxLabel);
                wp_contact_cx.set("contributeto", im.get("CONTACT CROSS SECTION"));
                wp_contact_cx.set("quickplane", "xz");
                wp_contact_cx.set("unite", true);
                wp_contact_cx.geom().create("r1", "Rectangle");
                wp_contact_cx.geom().feature("r1").label("Contact Cross Section");
                wp_contact_cx
                    .geom()
                    .feature("r1")
                    .set("pos", new String[] { "R_in+Ribbon_recess+Ribbon_thk/2", "Center" });
                wp_contact_cx.geom().feature("r1").set("base", "center");
                wp_contact_cx
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "Ribbon_thk", "Ribbon_z" });

                String revmcLabel = "Make Contact";
                GeomFeature rev_make_contact = model
                    .geom(id)
                    .create(im.next("rev", revmcLabel), "Revolve");
                rev_make_contact.label("Make Contact");
                rev_make_contact.set("contributeto", im.get("CONTACT FINAL"));
                rev_make_contact.set("angle1", "Rot_def");
                rev_make_contact.set("angle2", "Rot_def+Ribbon_theta");
                rev_make_contact.selection("input").named(im.get("CONTACT CROSS SECTION"));

                String ifrecessLabel = "IF RECESS";
                GeomFeature if_recess = model.geom(id).create(im.next("if", ifrecessLabel), "If");
                if_recess.set("condition", "Ribbon_recess>0");
                if_recess.label(ifrecessLabel);

                String wprcx1Label = "Recess Cross Section 1";
                GeomFeature wp_recess_cx1 = model
                    .geom(id)
                    .create(im.next("wp", wprcx1Label), "WorkPlane");
                wp_recess_cx1.label(wprcx1Label);
                wp_recess_cx1.set("contributeto", im.get("RECESS CROSS SECTION"));
                wp_recess_cx1.set("quickplane", "xz");
                wp_recess_cx1.set("unite", true);

                String cs1Label = "Cumulative Selection 1";
                wp_recess_cx1
                    .geom()
                    .selection()
                    .create(im.next("csel", cs1Label), "CumulativeSelection");
                wp_recess_cx1.geom().selection(im.get(cs1Label)).label(cs1Label);

                String rcxLabel = "wp RECESS CROSS SECTION";
                wp_recess_cx1
                    .geom()
                    .selection()
                    .create(im.next("csel", rcxLabel), "CumulativeSelection");
                wp_recess_cx1.geom().selection(im.get(rcxLabel)).label(rcxLabel);

                wp_recess_cx1.geom().create("r1", "Rectangle");
                wp_recess_cx1.geom().feature("r1").label("Recess Cross Section");
                wp_recess_cx1.geom().feature("r1").set("contributeto", im.get(rcxLabel));
                wp_recess_cx1
                    .geom()
                    .feature("r1")
                    .set("pos", new String[] { "R_in+Ribbon_recess/2", "Center" });
                wp_recess_cx1.geom().feature("r1").set("base", "center");
                wp_recess_cx1
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "Ribbon_recess", "Ribbon_z" });

                String revmrLabel = "Make Recess";
                GeomFeature rev_make_racess = model
                    .geom(id)
                    .create(im.next("rev", revmrLabel), "Revolve");
                rev_make_racess.label(revmrLabel);
                rev_make_racess.set("contributeto", im.get("RECESS FINAL"));
                rev_make_racess.set("angle1", "Rot_def");
                rev_make_racess.set("angle2", "Rot_def+Ribbon_theta");
                rev_make_racess.selection("input").named(im.get("RECESS CROSS SECTION"));

                endifLabel = "EndIf";
                model.geom(id).create(im.next("endif"), endifLabel).label(endifLabel);

                String srcLabel = "Src";
                GeomFeature src = model.geom(id).create(im.next("pt", srcLabel), "Point");
                src.label(srcLabel);
                src.set("contributeto", im.get("SRC"));
                src.set(
                    "p",
                    new String[] {
                        "(R_in+Ribbon_recess+Ribbon_thk/2)*cos(Rot_def+Ribbon_theta/2)",
                        "(R_in+Ribbon_recess+Ribbon_thk/2)*sin(Rot_def+Ribbon_theta/2)",
                        "Center",
                    }
                );

                model.geom(id).run();

                break;
            case "TubeCuffSweep_Primitive":
                mp.set("Cuff_thk", "0.1 [mm]");
                mp.set("Cuff_z", "3 [mm]");
                mp.set("R_in", "1 [mm]");
                mp.set("Center", "10 [mm]");
                mp.set("Cuff_theta", "300 [deg]");
                mp.set("Rot_def", "0 [deg]");

                im.labels =
                    new String[] {
                        "CUFF CROSS SECTION", //0
                        "CUFF FINAL",
                    };

                for (String cselCuSLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselCuSLabel), "CumulativeSelection")
                        .label(cselCuSLabel);
                }

                String wpccxLabel_CuS = "Cuff Cross Section";
                GeomFeature wp_cuff_cx = model
                    .geom(id)
                    .create(im.next("wp", wpccxLabel_CuS), "WorkPlane");
                wp_cuff_cx.label(wpccxLabel_CuS);
                wp_cuff_cx.set("contributeto", im.get("CUFF CROSS SECTION"));
                wp_cuff_cx.set("quickplane", "xz");
                wp_cuff_cx.set("unite", true);
                wp_cuff_cx.geom().create("r1", "Rectangle");
                wp_cuff_cx.geom().feature("r1").label("Cuff Cross Section");
                wp_cuff_cx
                    .geom()
                    .feature("r1")
                    .set("pos", new String[] { "R_in+Cuff_thk/2", "Center" });
                wp_cuff_cx.geom().feature("r1").set("base", "center");
                wp_cuff_cx.geom().feature("r1").set("size", new String[] { "Cuff_thk", "Cuff_z" });

                String revmcLabel_CuS = "Make Cuff";
                GeomFeature rev_make_cuff = model
                    .geom(id)
                    .create(im.next("rev", revmcLabel_CuS), "Revolve");
                rev_make_cuff.label("Make Cuff");
                rev_make_cuff.set("contributeto", im.get("CUFF FINAL"));
                rev_make_cuff.set("angle1", "Rot_def");
                rev_make_cuff.set("angle2", "Rot_def+Cuff_theta");
                rev_make_cuff.selection("input").named(im.get("CUFF CROSS SECTION"));

                model.geom(id).run();

                break;
            case "WireContact_Primitive":
                model.geom(id).inputParam().set("Wire_r", "37.5 [um]");
                model.geom(id).inputParam().set("R_in", "250 [um]");
                model.geom(id).inputParam().set("Center", "10 [mm]");
                model.geom(id).inputParam().set("Pitch", "1 [mm]");
                model.geom(id).inputParam().set("Wire_sep", "10 [um]");
                model.geom(id).inputParam().set("Wire_theta", "250 [deg]");

                im.labels = new String[] { "CONTACT CROSS SECTION", "CONTACT FINAL", "SRC" };

                for (String cselWCLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselWCLabel), "CumulativeSelection")
                        .label(cselWCLabel);
                }

                String contactxsLabel = "Contact Cross Section";
                GeomFeature contact_xs = model
                    .geom(id)
                    .create(im.next("wp", contactxsLabel), "WorkPlane");
                contact_xs.set("contributeto", im.get("CONTACT CROSS SECTION"));
                contact_xs.label(contactxsLabel);
                contact_xs.set("quickplane", "zx");
                contact_xs.set("unite", true);
                contact_xs
                    .geom()
                    .selection()
                    .create(im.get("CONTACT CROSS SECTION"), "CumulativeSelection");
                contact_xs
                    .geom()
                    .selection(im.get("CONTACT CROSS SECTION"))
                    .label("CONTACT CROSS SECTION");
                contact_xs.geom().create("c1", "Circle");
                contact_xs.geom().feature("c1").label("Contact Cross Section");
                contact_xs
                    .geom()
                    .feature("c1")
                    .set("contributeto", im.get("CONTACT CROSS SECTION"));
                contact_xs
                    .geom()
                    .feature("c1")
                    .set("pos", new String[] { "Center", "R_in-Wire_r-Wire_sep" });
                contact_xs.geom().feature("c1").set("r", "Wire_r");

                String mcLabel = "Make Contact";
                GeomFeature contact = model.geom(id).create(im.next("rev", mcLabel), "Revolve");
                contact.label(mcLabel);
                contact.set("contributeto", im.get("CONTACT FINAL"));
                contact.set("angle2", "Wire_theta");
                contact.set("axis", new int[] { 1, 0 });
                contact.selection("input").named(im.get("CONTACT CROSS SECTION"));

                String sourceLabel = "Src";
                GeomFeature source = model.geom(id).create(im.next("pt", sourceLabel), "Point");
                source.label(sourceLabel);
                source.set("contributeto", im.get("SRC"));
                source.set(
                    "p",
                    new String[] {
                        "(R_in-Wire_r-Wire_sep)*cos(Wire_theta/2)",
                        "(R_in-Wire_r-Wire_sep)*sin(Wire_theta/2)",
                        "Center",
                    }
                );

                model.geom(id).run();

                break;
            case "CircleContact_Primitive":
                model.geom(id).inputParam().set("Circle_recess", "0.05 [mm]");
                model.geom(id).inputParam().set("Rotation_angle", "0 [deg]");
                model.geom(id).inputParam().set("Center", "20 [mm]");
                model.geom(id).inputParam().set("Circle_def", "1");
                model.geom(id).inputParam().set("R_in", "1.5 [mm]");
                model.geom(id).inputParam().set("Circle_thk", "0.05 [mm]");
                model.geom(id).inputParam().set("Overshoot", "0.05 [mm]");
                model.geom(id).inputParam().set("Circle_diam", "2 [mm]");
                model.geom(id).inputParam().set("L", "0.354 [inch]");

                im.labels =
                    new String[] {
                        "CONTACT CUTTER IN", //0
                        "PRE CUT CONTACT",
                        "RECESS FINAL",
                        "RECESS OVERSHOOT",
                        "SRC",
                        "PLANE FOR CONTACT", //5
                        "CONTACT FINAL",
                        "CONTACT CUTTER OUT",
                        "BASE CONTACT PLANE (PRE ROTATION)",
                        "PLANE FOR RECESS",
                        "PRE CUT RECESS", //10
                        "RECESS CUTTER IN",
                        "RECESS CUTTER OUT",
                        "BASE PLANE (PRE ROTATION)",
                    };

                for (String cselCCLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselCCLabel), "CumulativeSelection")
                        .label(cselCCLabel);
                }

                String bpprLabel = "Base Plane (Pre Rrotation)";
                GeomFeature baseplane_prerot = model
                    .geom(id)
                    .create(im.next("wp", bpprLabel), "WorkPlane");
                baseplane_prerot.label(bpprLabel);
                baseplane_prerot.set("contributeto", im.get("BASE PLANE (PRE ROTATION)"));
                baseplane_prerot.set("quickplane", "yz");
                baseplane_prerot.set("unite", true);
                baseplane_prerot.set("showworkplane", false);

                String ifrecessCCLabel = "If Recess";
                GeomFeature ifrecessCC = model
                    .geom(id)
                    .create(im.next("if", ifrecessCCLabel), "If");
                ifrecessCC.label(ifrecessCCLabel);
                ifrecessCC.set("condition", "Circle_recess>0");

                String rprLabel = "Rotated Plane (for Recess)";
                GeomFeature rpr = model.geom(id).create(im.next("wp", rprLabel), "WorkPlane");
                rpr.label(rprLabel);
                rpr.set("contributeto", im.get("PLANE FOR RECESS"));
                rpr.set("planetype", "transformed");
                rpr.set("workplane", im.get(bpprLabel));
                rpr.set("transaxis", new int[] { 0, 1, 0 });
                rpr.set("transrot", "Rotation_angle");
                rpr.set("unite", true);

                String cosLabel = "CONTACT OUTLINE SHAPE";
                rpr.geom().selection().create(im.next("csel", cosLabel), "CumulativeSelection");
                rpr.geom().selection(im.get(cosLabel)).label(cosLabel);

                String ifcsicLabel = "If Contact Surface is Circle (for recess)";
                GeomFeature ifcsic = rpr.geom().create(im.next("if", ifcsicLabel), "If");
                ifcsic.label(ifcsicLabel);
                ifcsic.set("condition", "Circle_def==1");

                String coLabel = "Contact Outline";
                GeomFeature co = rpr.geom().create(im.next("e", coLabel), "Ellipse");
                co.label("Contact Outline (for recess)");
                co.set("contributeto", im.get("CONTACT OUTLINE SHAPE"));
                co.set("pos", new String[] { "0", "Center" });
                co.set(
                    "semiaxes",
                    new String[] {
                        "(R_in+Circle_recess)*sin((Circle_diam)/(2*(R_in+Circle_recess)))",
                        "Circle_diam/2",
                    }
                );

                String elifcocLabel = "Else If Contact Outline is Circle";
                GeomFeature elifcoc = rpr.geom().create(im.next("elseif", elifcocLabel), "ElseIf");
                elifcoc.label("Else If Contact Outline is Circle (for recess)");
                elifcoc.set("condition", "Circle_def==2");

                String co1Label = "Contact Outline 1 (for recess)";
                GeomFeature co1 = rpr.geom().create(im.next("e", co1Label), "Ellipse");
                co1.label(co1Label);
                co1.set("contributeto", im.get("CONTACT OUTLINE SHAPE"));
                co1.set("pos", new String[] { "0", "Center" });
                co1.set("semiaxes", new String[] { "Circle_diam/2", "Circle_diam/2" });
                rpr.geom().create(im.next("endif"), "EndIf");

                String mpcrdLabel = "Make Pre Cut Recess Domains";
                GeomFeature mpcrd = model.geom(id).create(im.next("ext", mpcrdLabel), "Extrude");
                mpcrd.label(mpcrdLabel);
                mpcrd.set("contributeto", im.get("PRE CUT RECESS"));
                mpcrd.setIndex("distance", "R_in+Circle_recess+Overshoot", 0);
                mpcrd.selection("input").named(im.get("PLANE FOR RECESS"));

                String rciLabel = "Recess Cut In";
                GeomFeature rci = model.geom(id).create(im.next("cyl", rciLabel), "Cylinder");
                rci.label(rciLabel);
                rci.set("contributeto", im.get("RECESS CUTTER IN"));
                rci.set("pos", new String[] { "0", "0", "Center-L/2" });
                rci.set("r", "R_in");
                rci.set("h", "L");

                String rcoLabel = "Recess Cut Out";
                GeomFeature rco = model.geom(id).create(im.next("cyl", rcoLabel), "Cylinder");
                rco.label(rcoLabel);
                rco.set("contributeto", im.get("RECESS CUTTER OUT"));
                rco.set("pos", new String[] { "0", "0", "Center-L/2" });
                rco.set("r", "R_in+Circle_recess");
                rco.set("h", "L");
                rco.set("selresult", false);
                rco.set("selresultshow", false);

                String erciLabel = "Execute Recess Cut In";
                GeomFeature erci = model.geom(id).create(im.next("dif", erciLabel), "Difference");
                erci.label(erciLabel);
                erci.set("contributeto", im.get("RECESS FINAL"));
                erci.selection("input").named(im.get("PRE CUT RECESS"));
                erci.selection("input2").named(im.get("RECESS CUTTER IN"));

                String pordLabel = "Partition Outer Recess Domain";
                GeomFeature pord = model
                    .geom(id)
                    .create(im.next("pard", pordLabel), "PartitionDomains");
                pord.label(pordLabel);
                pord.set("contributeto", im.get("RECESS FINAL"));
                pord.set("partitionwith", "objects");
                pord.set("keepobject", false);
                pord.selection("domain").named(im.get("PRE CUT RECESS"));
                pord.selection("object").named(im.get("RECESS CUTTER OUT"));

                String soLabel = "Select Overshoot";
                GeomFeature so = model
                    .geom(id)
                    .create(im.next("ballsel", soLabel), "BallSelection");
                so.label(soLabel);
                so.set("posx", "(R_in+Circle_recess+Overshoot/2)*cos(Rotation_angle)");
                so.set("posy", "(R_in+Circle_recess+Overshoot/2)*sin(Rotation_angle)");
                so.set("posz", "Center");
                so.set("r", 1);
                so.set("contributeto", im.get("RECESS OVERSHOOT"));
                so.set("selkeep", false);

                String droLabel = "Delete Recess Overshoot";
                GeomFeature dro = model.geom(id).create(im.next("del", droLabel), "Delete");
                dro.label(droLabel);
                dro.selection("input").init(3);
                dro.selection("input").named(im.get("RECESS OVERSHOOT"));

                String endifrecessLabel = "EndIf";
                model.geom(id).create(im.next("endif"), endifrecessLabel);

                String rpcLabel = "Rotated Plane for Contact";
                GeomFeature rpc = model.geom(id).create(im.next("wp", rpcLabel), "WorkPlane");
                rpc.label(rpcLabel);
                rpc.set("contributeto", im.get("PLANE FOR CONTACT"));
                rpc.set("planetype", "transformed");
                rpc.set("workplane", im.get("Base Plane (Pre Rrotation)"));
                rpc.set("transaxis", new int[] { 0, 1, 0 });
                rpc.set("transrot", "Rotation_angle");
                rpc.set("unite", true);

                String coscLabel = "wp CONTACT OUTLINE SHAPE";
                rpc.geom().selection().create(im.next("csel", coscLabel), "CumulativeSelection");
                rpc.geom().selection(im.get(coscLabel)).label(coscLabel);

                String ifcsiccLabel = "If Contact Surface is Circle (for contact)";
                GeomFeature icsicc = rpc.geom().create(im.next("if", ifcsiccLabel), "If");
                icsicc.label(ifcsiccLabel);
                icsicc.set("condition", "Circle_def==1");

                String cocLabel = "Contact Outline (for contact)";
                GeomFeature coc = rpc.geom().create(im.next("e", cocLabel), "Ellipse");
                coc.label(cocLabel);
                coc.set("contributeto", im.get(coscLabel));
                coc.set("pos", new String[] { "0", "Center" });
                coc.set(
                    "semiaxes",
                    new String[] {
                        "(R_in+Circle_recess)*sin((Circle_diam)/(2*(R_in+Circle_recess)))",
                        "Circle_diam/2",
                    }
                ); //

                String elifcoccLabel = "Else If Contact Outline is Circle (for contact)";
                GeomFeature elifcocc = rpc
                    .geom()
                    .create(im.next("elseif", elifcoccLabel), "ElseIf");
                elifcocc.label(elifcoccLabel);
                elifcocc.set("condition", "Circle_def==2");

                String co1cLabel = "Contact Outline 1 (for contact)";
                GeomFeature co1c = rpc.geom().create(im.next("e", co1cLabel), "Ellipse");
                co1c.label(co1cLabel);
                co1c.set("contributeto", im.get(coscLabel));
                co1c.set("pos", new String[] { "0", "Center" });
                co1c.set("semiaxes", new String[] { "Circle_diam/2", "Circle_diam/2" });
                rpc.geom().create(im.next("endif"), "EndIf");

                String mpccdLabel = "Make Pre Cut Contact Domains";
                GeomFeature mpccd = model.geom(id).create(im.next("ext", mpccdLabel), "Extrude");
                mpccd.label(mpccdLabel);
                mpccd.set("contributeto", im.get("PRE CUT CONTACT"));
                mpccd.setIndex("distance", "R_in+Circle_recess+Circle_thk+Overshoot", 0);
                mpccd.selection("input").named(im.get("PLANE FOR CONTACT"));

                String cciLabel = "Contact Cut In";
                GeomFeature cci = model.geom(id).create(im.next("cyl", cciLabel), "Cylinder");
                cci.label(cciLabel);
                cci.set("contributeto", im.get("CONTACT CUTTER IN"));
                cci.set("pos", new String[] { "0", "0", "Center-L/2" });
                cci.set("r", "R_in+Circle_recess");
                cci.set("h", "L");

                String ccoLabel = "Contact Cut Out";
                GeomFeature cco = model.geom(id).create(im.next("cyl", ccoLabel), "Cylinder");
                cco.label(ccoLabel);
                cco.set("contributeto", im.get("CONTACT CUTTER OUT"));
                cco.set("pos", new String[] { "0", "0", "Center-L/2" });
                cco.set("r", "R_in+Circle_recess+Circle_thk");
                cco.set("h", "L");

                String ecciLabel = "Execute Contact Cut In";
                GeomFeature ecci = model.geom(id).create(im.next("dif", ecciLabel), "Difference");
                ecci.label(ecciLabel);
                ecci.set("contributeto", im.get("CONTACT FINAL"));
                ecci.selection("input").named(im.get("PRE CUT CONTACT"));
                ecci.selection("input2").named(im.get("CONTACT CUTTER IN"));

                String pocdLabel = "Partition Outer Contact Domain";
                GeomFeature pocd = model
                    .geom(id)
                    .create(im.next("pard", pocdLabel), "PartitionDomains");
                pocd.label(pocdLabel);
                pocd.set("contributeto", im.get("CONTACT FINAL"));
                pocd.set("partitionwith", "objects");
                pocd.set("keepobject", false);
                pocd.selection("domain").named(im.get("PRE CUT CONTACT"));
                pocd.selection("object").named(im.get("CONTACT CUTTER OUT"));

                String so1Label = "Select Overshoot 1";
                GeomFeature so1 = model
                    .geom(id)
                    .create(im.next("ballsel", so1Label), "BallSelection");
                so1.label(so1Label);
                so1.set("posx", "(R_in+Circle_recess+Circle_thk+Overshoot/2)*cos(Rotation_angle)");
                so1.set("posy", "(R_in+Circle_recess+Circle_thk+Overshoot/2)*sin(Rotation_angle)");
                so1.set("posz", "Center");
                so1.set("r", 1);
                so1.set("contributeto", im.get("RECESS OVERSHOOT"));
                so1.set("selkeep", false);

                String dro1Label = "Delete Recess Overshoot 1";
                GeomFeature dro1 = model.geom(id).create(im.next("del", dro1Label), "Delete");
                dro1.label(dro1Label);
                dro1.selection("input").init(3);
                dro1.selection("input").named(im.get("RECESS OVERSHOOT"));

                String srccLabel = "Src";
                GeomFeature srcc = model.geom(id).create(im.next("pt", srccLabel), "Point");
                srcc.label(srccLabel);
                srcc.set("contributeto", im.get("SRC"));
                srcc.set(
                    "p",
                    new String[] {
                        "(R_in+Circle_recess+Circle_thk/2)*cos(Rotation_angle)",
                        "(R_in+Circle_recess+Circle_thk/2)*sin(Rotation_angle)",
                        "Center",
                    }
                );

                model.geom(id).run();

                break;
            case "HelicalContact_Primitive": // hack
                model.geom(id).inputParam().set("Center", "20 [mm]");
                model.geom(id).inputParam().set("Corr", "0 [deg]");

                im.labels =
                    new String[] {
                        "PC1", //0
                        "Cuffp1",
                        "SEL END P1",
                        "PC2",
                        "SRC",
                        "Cuffp2", //5
                        "Conductorp2",
                        "SEL END P2",
                        "Cuffp3",
                        "PC3",
                        "CUFF FINAL", //10
                    };

                for (String cselHCCLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselHCCLabel), "CumulativeSelection")
                        .label(cselHCCLabel);
                }

                String hc_hicsp1Label = "Helical Insulator Cross Section Part 1";
                GeomFeature hc_hicsp1 = model
                    .geom(id)
                    .create(im.next("wp", hc_hicsp1Label), "WorkPlane");
                hc_hicsp1.label(hc_hicsp1Label);
                hc_hicsp1.set("quickplane", "xz");
                hc_hicsp1.set("unite", true);

                String hc_hicsLabel = "HELICAL INSULATOR CROSS SECTION";
                hc_hicsp1
                    .geom()
                    .selection()
                    .create(im.next("csel", hc_hicsLabel), "CumulativeSelection");
                hc_hicsp1.geom().selection(im.get(hc_hicsLabel)).label(hc_hicsLabel);

                String hc_hicxp1Label = "HELICAL INSULATOR CROSS SECTION P1";
                hc_hicsp1
                    .geom()
                    .selection()
                    .create(im.next("csel", hc_hicxp1Label), "CumulativeSelection");
                hc_hicsp1.geom().selection(im.get(hc_hicxp1Label)).label(hc_hicxp1Label);
                hc_hicsp1.geom().create("r1", "Rectangle");
                hc_hicsp1.geom().feature("r1").label("Helical Insulator Cross Section Part 1");
                hc_hicsp1.geom().feature("r1").set("contributeto", im.get(hc_hicxp1Label));
                hc_hicsp1
                    .geom()
                    .feature("r1")
                    .set(
                        "pos",
                        new String[] { "r_cuff_in_LN+(thk_cuff_LN/2)", "Center-(L_cuff_LN/2)" }
                    );
                hc_hicsp1.geom().feature("r1").set("base", "center");
                hc_hicsp1
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "thk_cuff_LN", "w_cuff_LN" });

                String hc_pcp1Label = "Parametric Curve Part 1";
                GeomFeature hc_pcp1 = model
                    .geom(id)
                    .create(im.next("pc", hc_pcp1Label), "ParametricCurve");
                hc_pcp1.label(hc_pcp1Label);
                hc_pcp1.set("contributeto", im.get("PC1"));
                hc_pcp1.set("parmax", "rev_cuff_LN*(0.75/2.5)");
                hc_pcp1.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "sin(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "Center+(L_cuff_LN)*(s/rev_cuff_LN)-(L_cuff_LN/2)",
                    }
                );

                String hc_mcp1Label = "Make Cuff Part 1";
                GeomFeature hc_mcp1 = model.geom(id).create(im.next("swe", hc_mcp1Label), "Sweep");
                hc_mcp1.label("Make Cuff Part 1");
                hc_mcp1.set("contributeto", im.get("Cuffp1"));
                hc_mcp1.set("crossfaces", true);
                hc_mcp1.set("keep", false);
                hc_mcp1.set("includefinal", false);
                hc_mcp1.set("twistcomp", false);
                hc_mcp1
                    .selection("face")
                    .named(im.get(hc_hicsp1Label) + "_" + im.get(hc_hicxp1Label));
                hc_mcp1.selection("edge").named(im.get("PC1"));
                hc_mcp1.selection("diredge").set(im.get(hc_pcp1Label) + "(1)", 1);

                String hc_sefp1Label = "Select End Face Part 1";
                GeomFeature hc_sefp1 = model
                    .geom(id)
                    .create(im.next("ballsel", hc_sefp1Label), "BallSelection");
                hc_sefp1.set("entitydim", 2);
                hc_sefp1.label(hc_sefp1Label);
                hc_sefp1.set(
                    "posx",
                    "cos(2*pi*rev_cuff_LN*((0.75)/2.5))*((thk_cuff_LN/2)+r_cuff_in_LN)"
                );
                hc_sefp1.set(
                    "posy",
                    "sin(2*pi*rev_cuff_LN*((0.75)/2.5))*((thk_cuff_LN/2)+r_cuff_in_LN)"
                );
                hc_sefp1.set(
                    "posz",
                    "Center+(L_cuff_LN)*(rev_cuff_LN*((0.75)/2.5)/rev_cuff_LN)-(L_cuff_LN/2)"
                );
                hc_sefp1.set("r", 1);
                hc_sefp1.set("contributeto", im.get("SEL END P1"));

                //                String hicsp2Label = "Helical Insulator Cross Section Part 2";
                GeomFeature hc_hicsp2 = model
                    .geom(id)
                    .create(im.next("wp", "Helical Insulator Cross Section Part 2"), "WorkPlane");
                String hc_hccsp2wpLabel = "HELICAL CONDUCTOR CROSS SECTION P2";
                hc_hicsp2
                    .geom()
                    .selection()
                    .create(im.next("csel", hc_hccsp2wpLabel), "CumulativeSelection");
                hc_hicsp2.geom().selection(im.get(hc_hccsp2wpLabel)).label(hc_hccsp2wpLabel);

                String hc_hccsp2Label = "Helical Conductor Cross Section Part 2";
                GeomFeature hc_hccsp2 = model
                    .geom(id)
                    .create(im.next("wp", hc_hccsp2Label), "WorkPlane");
                hc_hccsp2.label(hc_hccsp2Label);
                hc_hccsp2.set("planetype", "faceparallel");
                hc_hccsp2.set("unite", true);
                hc_hccsp2.selection("face").named(im.get("SEL END P1"));

                String hc_hccxp2Label = "wp HELICAL CONDUCTOR CROSS SECTION P2";
                hc_hccsp2
                    .geom()
                    .selection()
                    .create(im.next("csel", hc_hccxp2Label), "CumulativeSelection");
                hc_hccsp2.geom().selection(im.get(hc_hccxp2Label)).label(hc_hccxp2Label);
                hc_hccsp2.geom().create("r2", "Rectangle");
                hc_hccsp2.geom().feature("r2").label("Helical Conductor Cross Section Part 2");
                hc_hccsp2.geom().feature("r2").set("contributeto", im.get(hc_hccxp2Label));
                hc_hccsp2
                    .geom()
                    .feature("r2")
                    .set("pos", new String[] { "(thk_elec_LN-thk_cuff_LN)/2", "0" });
                hc_hccsp2.geom().feature("r2").set("base", "center");
                hc_hccsp2
                    .geom()
                    .feature("r2")
                    .set("size", new String[] { "thk_elec_LN", "w_elec_LN" });

                String hc_pcp2Label = "Parametric Curve Part 2";
                GeomFeature hc_pcp2 = model
                    .geom(id)
                    .create(im.next("pc", hc_pcp2Label), "ParametricCurve");
                hc_pcp2.label(hc_pcp2Label);
                hc_pcp2.set("contributeto", im.get("PC2"));
                hc_pcp2.set("parmin", "rev_cuff_LN*(0.75/2.5)");
                hc_pcp2.set("parmax", "rev_cuff_LN*((0.75+1)/2.5)");
                hc_pcp2.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "sin(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "Center+(L_cuff_LN)*(s/rev_cuff_LN)-(L_cuff_LN/2)",
                    }
                );

                String hc_mcp2cLabel = "Make Conductor Part 2";
                GeomFeature hc_mcp2c = model
                    .geom(id)
                    .create(im.next("swe", hc_mcp2cLabel), "Sweep");
                hc_mcp2c.label(hc_mcp2cLabel);
                hc_mcp2c.set("contributeto", im.get("Conductorp2"));
                hc_mcp2c.set("crossfaces", true);
                hc_mcp2c.set("includefinal", false);
                hc_mcp2c.set("twistcomp", false);
                hc_mcp2c
                    .selection("face")
                    .named(im.get(hc_hccsp2Label) + "_" + im.get(hc_hccxp2Label));
                hc_mcp2c.selection("edge").named(im.get("PC2"));
                hc_mcp2c.selection("diredge").set(im.get(hc_pcp2Label) + "(1)", 1);

                String hc_srchLabel = "ptSRC";
                GeomFeature hc_srch = model.geom(id).create(im.next("pt", hc_srchLabel), "Point");
                hc_srch.label(hc_srchLabel);
                hc_srch.set("contributeto", im.get("SRC"));
                hc_srch.set(
                    "p",
                    new String[] {
                        "cos(2*pi*rev_cuff_LN*(1.25/2.5))*((thk_elec_LN/2)+r_cuff_in_LN)",
                        "sin(2*pi*rev_cuff_LN*(1.25/2.5))*((thk_elec_LN/2)+r_cuff_in_LN)",
                        "Center",
                    }
                );

                GeomFeature delInsulatorp1 = model.geom(id).create(im.next("del"), "Delete");
                delInsulatorp1.selection("input").init(3);
                delInsulatorp1.selection("input").named(im.get("Cuffp1"));

                model.geom(id).run();

                break;
            case "HelicalCuffnContact_Primitive":
                model.geom(id).inputParam().set("Center", "20 [mm]");
                model.geom(id).inputParam().set("Corr", "0 [deg]");
                model.geom(id).inputParam().set("rev_BD_insul", "0.75");
                model.geom(id).inputParam().set("rev_BD_cond", "1");

                im.labels =
                    new String[] {
                        "PC1", //0
                        "Cuffp1",
                        "SEL END P1",
                        "PC2",
                        "SRC",
                        "Cuffp2", //5
                        "Conductorp2",
                        "SEL END P2",
                        "Cuffp3",
                        "PC3",
                        "CUFF FINAL", //10
                    };

                for (String cselHCCLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselHCCLabel), "CumulativeSelection")
                        .label(cselHCCLabel);
                }

                String hicsp1Label = "Helical Insulator Cross Section Part 1";
                GeomFeature hicsp1 = model.geom(id).create(im.next("wp", hicsp1Label), "WorkPlane");
                hicsp1.label(hicsp1Label);
                hicsp1.set("quickplane", "xz");
                hicsp1.set("unite", true);

                String hicsLabel = "HELICAL INSULATOR CROSS SECTION";
                hicsp1.geom().selection().create(im.next("csel", hicsLabel), "CumulativeSelection");
                hicsp1.geom().selection(im.get(hicsLabel)).label(hicsLabel);

                String hicxp1Label = "HELICAL INSULATOR CROSS SECTION P1";
                hicsp1
                    .geom()
                    .selection()
                    .create(im.next("csel", hicxp1Label), "CumulativeSelection");
                hicsp1.geom().selection(im.get(hicxp1Label)).label(hicxp1Label);
                hicsp1.geom().create("r1", "Rectangle");
                hicsp1.geom().feature("r1").label("Helical Insulator Cross Section Part 1");
                hicsp1.geom().feature("r1").set("contributeto", im.get(hicxp1Label));
                hicsp1
                    .geom()
                    .feature("r1")
                    .set(
                        "pos",
                        new String[] { "r_cuff_in_LN+(thk_cuff_LN/2)", "Center-(L_cuff_LN/2)" }
                    );
                hicsp1.geom().feature("r1").set("base", "center");
                hicsp1
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "thk_cuff_LN", "w_cuff_LN" });

                String pcp1Label = "Parametric Curve Part 1";
                GeomFeature pcp1 = model
                    .geom(id)
                    .create(im.next("pc", pcp1Label), "ParametricCurve");
                pcp1.label(pcp1Label);
                pcp1.set("contributeto", im.get("PC1"));
                pcp1.set("parmax", "rev_cuff_LN*(rev_BD_insul/2.5)");
                pcp1.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "sin(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "Center+(L_cuff_LN)*(s/rev_cuff_LN)-(L_cuff_LN/2)",
                    }
                );

                String mcp1Label = "Make Cuff Part 1";
                GeomFeature mcp1 = model.geom(id).create(im.next("swe", mcp1Label), "Sweep");
                mcp1.label("Make Cuff Part 1");
                mcp1.set("contributeto", im.get("Cuffp1"));
                mcp1.set("crossfaces", true);
                mcp1.set("keep", false);
                mcp1.set("includefinal", false);
                mcp1.set("twistcomp", false);
                mcp1.selection("face").named(im.get(hicsp1Label) + "_" + im.get(hicxp1Label));
                mcp1.selection("edge").named(im.get("PC1"));
                mcp1.selection("diredge").set(im.get(pcp1Label) + "(1)", 1);

                String sefp1Label = "Select End Face Part 1";
                GeomFeature sefp1 = model
                    .geom(id)
                    .create(im.next("ballsel", sefp1Label), "BallSelection");
                sefp1.set("entitydim", 2);
                sefp1.label(sefp1Label);
                sefp1.set(
                    "posx",
                    "cos(2*pi*rev_cuff_LN*((rev_BD_insul)/2.5))*((thk_cuff_LN/2)+r_cuff_in_LN)"
                );
                sefp1.set(
                    "posy",
                    "sin(2*pi*rev_cuff_LN*((rev_BD_insul)/2.5))*((thk_cuff_LN/2)+r_cuff_in_LN)"
                );
                sefp1.set(
                    "posz",
                    "Center+(L_cuff_LN)*(rev_cuff_LN*((rev_BD_insul)/2.5)/rev_cuff_LN)-(L_cuff_LN/2)"
                );
                sefp1.set("r", 1);
                sefp1.set("contributeto", im.get("SEL END P1"));

                String hicsp2Label = "Helical Insulator Cross Section Part 2";
                GeomFeature hicsp2 = model
                    .geom(id)
                    .create(im.next("wp", "Helical Insulator Cross Section Part 2"), "WorkPlane");
                hicsp2.label(hicsp2Label);
                hicsp2.set("planetype", "faceparallel");
                hicsp2.set("unite", true);
                hicsp2.selection("face").named(im.get("SEL END P1"));

                String hicsp2wpLabel = "HELICAL INSULATOR CROSS SECTION P2";
                hicsp2
                    .geom()
                    .selection()
                    .create(im.next("csel", hicsp2wpLabel), "CumulativeSelection");
                hicsp2.geom().selection(im.get(hicsp2wpLabel)).label(hicsp2wpLabel);

                String hccsp2wpLabel = "HELICAL CONDUCTOR CROSS SECTION P2";
                hicsp2
                    .geom()
                    .selection()
                    .create(im.next("csel", hccsp2wpLabel), "CumulativeSelection");
                hicsp2.geom().selection(im.get(hccsp2wpLabel)).label(hccsp2wpLabel);

                hicsp2.geom().create("r1", "Rectangle");
                hicsp2.geom().feature("r1").label("Helical Insulator Cross Section Part 2");
                hicsp2.geom().feature("r1").set("contributeto", im.get(hicsp2wpLabel));
                hicsp2.geom().feature("r1").set("base", "center");
                hicsp2
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "thk_cuff_LN", "w_cuff_LN" });

                String hccsp2Label = "Helical Conductor Cross Section Part 2";
                GeomFeature hccsp2 = model.geom(id).create(im.next("wp", hccsp2Label), "WorkPlane");
                hccsp2.label(hccsp2Label);
                hccsp2.set("planetype", "faceparallel");
                hccsp2.set("unite", true);
                hccsp2.selection("face").named(im.get("SEL END P1"));

                String hicxp2Label = "wp HELICAL INSULATOR CROSS SECTION P2";
                hccsp2
                    .geom()
                    .selection()
                    .create(im.next("csel", hicxp2Label), "CumulativeSelection");
                hccsp2.geom().selection(im.get(hicxp2Label)).label(hicxp2Label);

                String hccxp2Label = "wp HELICAL CONDUCTOR CROSS SECTION P2";
                hccsp2
                    .geom()
                    .selection()
                    .create(im.next("csel", hccxp2Label), "CumulativeSelection");
                hccsp2.geom().selection(im.get(hccxp2Label)).label(hccxp2Label);
                hccsp2.geom().create("r2", "Rectangle");
                hccsp2.geom().feature("r2").label("Helical Conductor Cross Section Part 2");
                hccsp2.geom().feature("r2").set("contributeto", im.get(hccxp2Label));
                hccsp2
                    .geom()
                    .feature("r2")
                    .set("pos", new String[] { "(thk_elec_LN-thk_cuff_LN)/2", "0" });
                hccsp2.geom().feature("r2").set("base", "center");
                hccsp2
                    .geom()
                    .feature("r2")
                    .set("size", new String[] { "thk_elec_LN", "w_elec_LN" });

                String pcp2Label = "Parametric Curve Part 2";
                GeomFeature pcp2 = model
                    .geom(id)
                    .create(im.next("pc", pcp2Label), "ParametricCurve");
                pcp2.label(pcp2Label);
                pcp2.set("contributeto", im.get("PC2"));
                pcp2.set("parmin", "rev_cuff_LN*(rev_BD_insul/2.5)");
                pcp2.set("parmax", "rev_cuff_LN*((rev_BD_insul+rev_BD_cond)/2.5)");
                pcp2.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "sin(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "Center+(L_cuff_LN)*(s/rev_cuff_LN)-(L_cuff_LN/2)",
                    }
                );

                String mcp2Label = "Make Cuff Part 2";
                GeomFeature mcp2 = model.geom(id).create(im.next("swe", mcp2Label), "Sweep");
                mcp2.label("Make Cuff Part 2");
                mcp2.set("contributeto", im.get("Cuffp2"));
                mcp2.set("crossfaces", true);
                mcp2.set("includefinal", false);
                mcp2.set("twistcomp", false);
                mcp2.selection("face").named(im.get(hicsp2Label) + "_" + im.get(hicsp2wpLabel));
                mcp2.selection("edge").named(im.get("PC2"));
                mcp2.selection("diredge").set(im.get(pcp2Label) + "(1)", 1);

                String mcp2cLabel = "Make Conductor Part 2";
                GeomFeature mcp2c = model.geom(id).create(im.next("swe", mcp2cLabel), "Sweep");
                mcp2c.label(mcp2cLabel);
                mcp2c.set("contributeto", im.get("Conductorp2"));
                mcp2c.set("crossfaces", true);
                mcp2c.set("includefinal", false);
                mcp2c.set("twistcomp", false);
                mcp2c.selection("face").named(im.get(hccsp2Label) + "_" + im.get(hccxp2Label));
                mcp2c.selection("edge").named(im.get("PC2"));
                mcp2c.selection("diredge").set(im.get(pcp2Label) + "(1)", 1);

                String sefp2Label = "Select End Face Part 2";
                GeomFeature sefp2 = model
                    .geom(id)
                    .create(im.next("ballsel", sefp2Label), "BallSelection");
                sefp2.set("entitydim", 2);
                sefp2.label(sefp2Label);
                sefp2.set(
                    "posx",
                    "cos(2*pi*rev_cuff_LN*((rev_BD_insul+rev_BD_cond)/2.5))*((thk_cuff_LN/2)+r_cuff_in_LN)"
                );
                sefp2.set(
                    "posy",
                    "sin(2*pi*rev_cuff_LN*((rev_BD_insul+rev_BD_cond)/2.5))*((thk_cuff_LN/2)+r_cuff_in_LN)"
                );
                sefp2.set(
                    "posz",
                    "Center+(L_cuff_LN)*(rev_cuff_LN*((rev_BD_insul+rev_BD_cond)/2.5)/rev_cuff_LN)-(L_cuff_LN/2)"
                );
                sefp2.set("r", 1);
                sefp2.set("contributeto", im.get("SEL END P2"));

                String hicsp3Label = "Helical Insulator Cross Section Part 3";
                GeomFeature hicsp3 = model.geom(id).create(im.next("wp", hicsp3Label), "WorkPlane");
                hicsp3.label(hicsp3Label);
                hicsp3.set("planetype", "faceparallel");
                hicsp3.set("unite", true);
                hicsp3.selection("face").named(im.get("SEL END P2"));

                String hicssp3Label = "HELICAL INSULATOR CROSS SECTION P3";
                hicsp3
                    .geom()
                    .selection()
                    .create(im.next("csel", hicssp3Label), "CumulativeSelection");
                hicsp3.geom().selection(im.get(hicssp3Label)).label(hicssp3Label);
                hicsp3.geom().create("r1", "Rectangle");
                hicsp3.geom().feature("r1").label("Helical Insulator Cross Section Part 3");
                hicsp3.geom().feature("r1").set("contributeto", im.get(hicssp3Label));
                hicsp3.geom().feature("r1").set("base", "center");
                hicsp3
                    .geom()
                    .feature("r1")
                    .set("size", new String[] { "thk_cuff_LN", "w_cuff_LN" });

                String pcp3Label = "Parametric Curve Part 3";
                GeomFeature pcp3 = model
                    .geom(id)
                    .create(im.next("pc", pcp3Label), "ParametricCurve");
                pcp3.label(pcp3Label);
                pcp3.set("contributeto", im.get("PC3"));
                pcp3.set("parmin", "rev_cuff_LN*((rev_BD_insul+rev_BD_cond)/2.5)");
                pcp3.set("parmax", "rev_cuff_LN");
                pcp3.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "sin(2*pi*s)*((thk_cuff_LN/2)+r_cuff_in_LN)",
                        "Center+(L_cuff_LN)*(s/rev_cuff_LN)-(L_cuff_LN/2)",
                    }
                );

                String mcp3Label = "Make Cuff Part 3";
                GeomFeature mcp3 = model.geom(id).create(im.next("swe", mcp3Label), "Sweep");
                mcp3.label(mcp3Label);
                mcp3.set("contributeto", im.get("Cuffp3"));
                mcp3.selection("face").named(im.get(hicsp3Label) + "_" + im.get(hicssp3Label));
                mcp3.selection("edge").named(im.get("PC3"));
                mcp3.set("keep", false);
                mcp3.set("twistcomp", false);

                String srchLabel = "ptSRC";
                GeomFeature srch = model.geom(id).create(im.next("pt", srchLabel), "Point");
                srch.label(srchLabel);
                srch.set("contributeto", im.get("SRC"));
                srch.set(
                    "p",
                    new String[] {
                        "cos(2*pi*rev_cuff_LN*(1.25/2.5))*((thk_elec_LN/2)+r_cuff_in_LN)",
                        "sin(2*pi*rev_cuff_LN*(1.25/2.5))*((thk_elec_LN/2)+r_cuff_in_LN)",
                        "Center",
                    }
                );

                String uspLabel = "Union Silicone Parts";
                model.geom(id).create(im.next("uni", uspLabel), "Union");
                model
                    .geom(id)
                    .feature(im.get(uspLabel))
                    .selection("input")
                    .set(im.get(mcp1Label), im.get(mcp2Label), im.get(mcp3Label));
                model.geom(id).selection(im.get("CUFF FINAL")).label("CUFF FINAL");
                model.geom(id).feature(im.get(uspLabel)).set("contributeto", im.get("CUFF FINAL"));

                model.geom(id).run();

                break;
            case "RectangleContact_Primitive":
                model.geom(id).inputParam().set("Center", "0 [mm]");
                model.geom(id).inputParam().set("Rotation_angle", "0 [deg]");
                model.geom(id).inputParam().set("Rect_w", "0.475 [mm]");
                model.geom(id).inputParam().set("Rect_z", "0.475 [mm]");
                model.geom(id).inputParam().set("Rect_fillet", "0.1 [mm]");
                model.geom(id).inputParam().set("L_cuff", "4.1917 [mm]");
                model.geom(id).inputParam().set("R_in", "1.5 [mm]");
                model.geom(id).inputParam().set("Rect_recess", "0.018 [mm]");
                model.geom(id).inputParam().set("Rect_thk", "0.018 [mm]");
                model.geom(id).inputParam().set("Rect_def", "1");

                im.labels =
                    new String[] {
                        "OUTER CONTACT CUTTER", //0
                        "SEL INNER EXCESS CONTACT",
                        "INNER CONTACT CUTTER",
                        "SEL OUTER EXCESS RECESS",
                        "SEL INNER EXCESS RECESS",
                        "OUTER CUTTER", //5
                        "FINAL RECESS",
                        "RECESS CROSS SECTION",
                        "OUTER RECESS CUTTER",
                        "RECESS PRE CUTS",
                        "INNER RECESS CUTTER", //10
                        "FINAL CONTACT",
                        "SEL OUTER EXCESS CONTACT",
                        "SEL OUTER EXCESS",
                        "SEL INNER EXCESS",
                        "BASE CONTACT PLANE (PRE ROTATION)", //15
                        "SRC",
                        "CONTACT PRE CUTS",
                        "CONTACT CROSS SECTION",
                        "INNER CUFF CUTTER",
                        "OUTER CUFF CUTTER", //20
                        "FINAL",
                        "INNER CUTTER",
                    };

                for (String cselReCLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselReCLabel), "CumulativeSelection")
                        .label(cselReCLabel);
                }

                String bpprsLabel = "base plane (pre rotation)";
                GeomFeature bpprs = model.geom(id).create(im.next("wp", bpprsLabel), "WorkPlane");
                bpprs.label(bpprsLabel);
                bpprs.set("contributeto", im.get("BASE CONTACT PLANE (PRE ROTATION)"));
                bpprs.set("quickplane", "yz");
                bpprs.set("unite", true);

                String ccscLabel = "Contact Cross Section";
                GeomFeature ccsc = model.geom(id).create(im.next("wp", ccscLabel), "WorkPlane");
                ccsc.label(ccscLabel);
                ccsc.set("contributeto", im.get("CONTACT CROSS SECTION"));
                ccsc.set("planetype", "transformed");
                ccsc.set("workplane", im.get(bpprsLabel));
                ccsc.set("transaxis", new int[] { 0, 1, 0 });
                ccsc.set("transrot", "Rotation_angle");
                ccsc.set("unite", true);

                String ifcsitLabel = "If Contact Surface is True";
                GeomFeature ifcsit = ccsc.geom().create(im.next("if", ifcsitLabel), "If");
                ifcsit.label("If Contact Surface is True");
                ifcsit.set("condition", "Rect_def==1");

                String cpfLabel = "CONTACT PRE FILLET";
                ccsc.geom().selection().create(im.next("csel", cpfLabel), "CumulativeSelection");
                ccsc.geom().selection(im.get(cpfLabel)).label(cpfLabel);

                String cfLabel = "CONTACT FILLETED";
                ccsc.geom().selection().create(im.next("csel", cfLabel), "CumulativeSelection");
                ccsc.geom().selection(im.get(cfLabel)).label(cfLabel);

                ccsc.geom().create("r1", "Rectangle");
                ccsc.geom().feature("r1").label("Contact Pre Fillet Corners");
                ccsc.geom().feature("r1").set("contributeto", im.get(cpfLabel));
                ccsc.geom().feature("r1").set("pos", new int[] { 0, 0 });
                ccsc.geom().feature("r1").set("base", "center");
                ccsc.geom().feature("r1").set("size", new String[] { "Rect_w", "Rect_z" });

                String filletLabel = "Fillet Corners 1";
                GeomFeature fillet = ccsc.geom().create(im.next("fil", filletLabel), "Fillet");
                fillet.label(filletLabel);
                fillet.set("contributeto", im.get(cfLabel));
                fillet.set("radius", "Rect_fillet");
                fillet.selection("point").named(im.get(cpfLabel));

                String scaleLabel1 = "scLabel1";
                GeomFeature scale = ccsc.geom().create(im.next("sca", scaleLabel1), "Scale");
                scale.label(scaleLabel1);
                scale.set("type", "anisotropic");
                scale.set(
                    "factor",
                    new String[] {
                        "(2*(R_in+Rect_recess)*sin((Rect_w)/(2*(R_in+Rect_recess))))/Rect_w",
                        "1",
                    }
                );
                scale.selection("input").named(im.get(cfLabel));

                String elifcoitLabel = "Else If Contact Outline is True";
                GeomFeature elifcoit = ccsc
                    .geom()
                    .create(im.next("elseif", elifcoitLabel), "ElseIf");
                elifcoit.label("Else If Contact Outline is True");
                elifcoit.set("condition", "Rect_def==2");

                ccsc.geom().create("r2", "Rectangle");
                ccsc.geom().feature("r2").label("Contact Pre Fillet Corners2");
                ccsc.geom().feature("r2").set("contributeto", im.get(cpfLabel));
                ccsc.geom().feature("r2").set("pos", new int[] { 0, 0 });
                ccsc.geom().feature("r2").set("base", "center");
                ccsc.geom().feature("r2").set("size", new String[] { "Rect_w", "Rect_z" });

                String filletLabel2 = "Fillet Corners 2";
                GeomFeature fillet2 = ccsc.geom().create(im.next("fil", filletLabel2), "Fillet");
                fillet2.label(filletLabel2);
                fillet2.set("contributeto", im.get(cfLabel));
                fillet2.set("radius", "Rect_fillet");
                fillet2.selection("point").named(im.get(cpfLabel));
                ccsc.geom().create(im.next("endif"), "EndIf");

                ccsc.geom().create("mov1", "Move");
                ccsc.geom().feature("mov1").set("disply", "Center");
                ccsc.geom().feature("mov1").selection("input").named(im.get(cfLabel));

                String mcpcLabel = "Make Contact Pre Cuts";
                GeomFeature mcpc = model.geom(id).create(im.next("ext", mcpcLabel), "Extrude");
                mcpc.label("Make Contact Pre Cuts");
                mcpc.set("contributeto", im.get("CONTACT PRE CUTS"));
                mcpc.setIndex("distance", "2*R_in", 0);
                mcpc.selection("input").named(im.get("CONTACT CROSS SECTION"));

                String iccLabel = "Inner Contact Cutter";
                GeomFeature icc = model.geom(id).create(im.next("cyl", iccLabel), "Cylinder");
                icc.label(iccLabel);
                icc.set("contributeto", im.get("INNER CONTACT CUTTER"));
                icc.set("pos", new String[] { "0", "0", "-L_cuff/2+Center" });
                icc.set("r", "R_in+Rect_recess");
                icc.set("h", "L_cuff");

                String occLabel = "Outer Contact Cutter";
                GeomFeature occ = model.geom(id).create(im.next("cyl", occLabel), "Cylinder");
                occ.label(occLabel);
                occ.set("contributeto", im.get("OUTER CONTACT CUTTER"));
                occ.set("pos", new String[] { "0", "0", "-L_cuff/2+Center" });
                occ.set("r", "R_in+Rect_recess+Rect_thk");
                occ.set("h", "L_cuff");

                String coeLabel = "Cut Outer Excess";
                GeomFeature coe = model.geom(id).create(im.next("par", coeLabel), "Partition");
                coe.label(coeLabel);
                coe.set("contributeto", im.get("FINAL CONTACT"));
                coe.selection("input").named(im.get("CONTACT PRE CUTS"));
                coe.selection("tool").named(im.get("OUTER CONTACT CUTTER"));

                String cieLabel = "Cut Inner Excess";
                GeomFeature cie = model.geom(id).create(im.next("par", cieLabel), "Partition");
                cie.label(cieLabel);
                cie.set("contributeto", im.get("FINAL CONTACT"));
                cie.selection("input").named(im.get("CONTACT PRE CUTS"));
                cie.selection("tool").named(im.get("INNER CONTACT CUTTER"));

                String sieLabel = "sel inner excess";
                GeomFeature sie = model
                    .geom(id)
                    .create(im.next("ballsel", sieLabel), "BallSelection");
                sie.label(sieLabel);
                sie.set("posx", "((R_in+Rect_recess)/2)*cos(Rotation_angle)");
                sie.set("posy", "((R_in+Rect_recess)/2)*sin(Rotation_angle)");
                sie.set("posz", "Center");
                sie.set("r", 1);
                sie.set("contributeto", im.get("SEL INNER EXCESS CONTACT"));
                sie.set("selkeep", false);

                String soeLabel = "sel outer excess";
                GeomFeature soe = model
                    .geom(id)
                    .create(im.next("ballsel", soeLabel), "BallSelection");
                soe.label(soeLabel);
                soe.set(
                    "posx",
                    "((2*R_in-(R_in+Rect_recess+Rect_thk))/2+R_in+Rect_recess+Rect_thk)*cos(Rotation_angle)"
                );
                soe.set(
                    "posy",
                    "((2*R_in-(R_in+Rect_recess+Rect_thk))/2+R_in+Rect_recess+Rect_thk)*sin(Rotation_angle)"
                );
                soe.set("posz", "Center");
                soe.set("r", 1);
                soe.set("contributeto", im.get("SEL OUTER EXCESS CONTACT"));
                soe.set("selkeep", false);

                String diecLabel = "Delete Inner Excess Contact";
                GeomFeature diec = model.geom(id).create(im.next("del", diecLabel), "Delete");
                diec.label(diecLabel);
                diec.selection("input").init(3);
                diec.selection("input").named(im.get("SEL INNER EXCESS CONTACT"));

                String doecLabel = "Delete Outer Excess Contact";
                GeomFeature doec = model.geom(id).create(im.next("del", doecLabel), "Delete");
                doec.label(doecLabel);
                doec.selection("input").init(3);
                doec.selection("input").named(im.get("SEL OUTER EXCESS CONTACT"));

                String irsLabel = "If Recess";
                GeomFeature irs = model.geom(id).create(im.next("if", irsLabel), "If");
                irs.set("condition", "Rect_recess>0");
                irs.label(irsLabel);

                String rcsLabel = "Recess Cross Section";
                GeomFeature rcs = model.geom(id).create(im.next("wp", rcsLabel), "WorkPlane");
                rcs.label(rcsLabel);
                rcs.set("contributeto", im.get("RECESS CROSS SECTION"));
                rcs.set("planetype", "transformed");
                rcs.set("workplane", im.get("base plane (pre rotation)"));
                rcs.set("transaxis", new int[] { 0, 1, 0 });
                rcs.set("transrot", "Rotation_angle");
                rcs.set("unite", true);

                String ifcsitfrLabel = "If Contact Surface is True (for recess)";
                GeomFeature ifcsitfr = rcs.geom().create(im.next("if", ifcsitfrLabel), "If");
                ifcsitfr.label("If Contact Surface is True (for recess)");
                ifcsitfr.set("condition", "Rect_def==1");

                String rpfrLabel = "RECESS PRE FILLET";
                rcs.geom().selection().create(im.next("csel", rpfrLabel), "CumulativeSelection");
                rcs.geom().selection(im.get(rpfrLabel)).label(rpfrLabel);

                String rfrLabel = "RECESS FILLETED";
                rcs.geom().selection().create(im.next("csel", rfrLabel), "CumulativeSelection");
                rcs.geom().selection(im.get(rfrLabel)).label(rfrLabel);

                rcs.geom().create("r1", "Rectangle");
                rcs.geom().feature("r1").label("Recess Pre Fillet Corners");
                rcs.geom().feature("r1").set("contributeto", im.get(rpfrLabel));
                rcs.geom().feature("r1").set("pos", new int[] { 0, 0 });
                rcs.geom().feature("r1").set("base", "center");
                rcs.geom().feature("r1").set("size", new String[] { "Rect_w", "Rect_z" });

                String filletrLabel = "Fillet Corners (for recess)";
                GeomFeature filletr = rcs.geom().create(im.next("fil", filletrLabel), "Fillet");
                filletr.label(filletrLabel);
                filletr.set("contributeto", im.get(rfrLabel));
                filletr.set("radius", "Rect_fillet");
                filletr.selection("point").named(im.get(rpfrLabel));

                String scalerLabel = "scrLabel";
                GeomFeature scaler = rcs.geom().create(im.next("sca", scalerLabel), "Scale");
                scaler.label(scalerLabel);
                scaler.set("type", "anisotropic");
                scaler.set(
                    "factor",
                    new String[] {
                        "(2*(R_in+Rect_recess)*sin((Rect_w)/(2*(R_in+Rect_recess))))/Rect_w",
                        "1",
                    }
                );
                scaler.selection("input").named(im.get(rfrLabel));

                String elifcotLabel = "Else If Contact Outline is True (for recess)";
                GeomFeature elifcot = rcs.geom().create(im.next("elseif", elifcotLabel), "ElseIf");
                elifcot.label(elifcotLabel);
                elifcot.set("condition", "Rect_def==2");

                rcs.geom().create("r2", "Rectangle");
                rcs.geom().feature("r2").label("Recess Pre Fillet Corners2");
                rcs.geom().feature("r2").set("contributeto", im.get(rpfrLabel));
                rcs.geom().feature("r2").set("pos", new int[] { 0, 0 });
                rcs.geom().feature("r2").set("base", "center");
                rcs.geom().feature("r2").set("size", new String[] { "Rect_w", "Rect_z" });

                String filletrLabel2 = "Fillet Corners (for recess)2";
                GeomFeature filletr2 = rcs.geom().create(im.next("fil", filletrLabel2), "Fillet");
                filletr2.label(filletrLabel2);
                filletr2.set("contributeto", im.get(rfrLabel));
                filletr2.set("radius", "Rect_fillet");
                filletr2.selection("point").named(im.get(rpfrLabel));
                rcs.geom().create(im.next("endif"), "EndIf");

                rcs.geom().create("mov1", "Move");
                rcs.geom().feature("mov1").set("disply", "Center");
                rcs.geom().feature("mov1").selection("input").named(im.get(rfrLabel));

                String mrpc1Label = "Make Recess Pre Cuts 1";
                GeomFeature mrpc1 = model.geom(id).create(im.next("ext", mrpc1Label), "Extrude");
                mrpc1.label(mrpc1Label);
                mrpc1.set("contributeto", im.get("RECESS PRE CUTS"));
                mrpc1.setIndex("distance", "2*R_in", 0);
                mrpc1.selection("input").named(im.get("RECESS CROSS SECTION"));

                String ircLabel = "Inner Recess Cutter";
                GeomFeature irc = model.geom(id).create(im.next("cyl", ircLabel), "Cylinder");
                irc.label(ircLabel);
                irc.set("contributeto", im.get("INNER RECESS CUTTER"));
                irc.set("pos", new String[] { "0", "0", "-L_cuff/2+Center" });
                irc.set("r", "R_in");
                irc.set("h", "L_cuff");

                String orcLabel = "Outer Recess Cutter";
                GeomFeature orc = model.geom(id).create(im.next("cyl", orcLabel), "Cylinder");
                orc.label(orcLabel);
                orc.set("contributeto", im.get("OUTER RECESS CUTTER"));
                orc.set("pos", new String[] { "0", "0", "-L_cuff/2+Center" });
                orc.set("r", "R_in+Rect_recess");
                orc.set("h", "L_cuff");

                String roreLabel = "Remove Outer Recess Excess";
                GeomFeature rore = model.geom(id).create(im.next("par", roreLabel), "Partition");
                rore.label(roreLabel);
                rore.set("contributeto", im.get("FINAL RECESS"));
                rore.selection("input").named(im.get("RECESS PRE CUTS"));
                rore.selection("tool").named(im.get("OUTER RECESS CUTTER"));

                String rireLabel = "Remove Inner Recess Excess";
                GeomFeature rire = model.geom(id).create(im.next("par", rireLabel), "Partition");
                rire.label(rireLabel);
                rire.set("contributeto", im.get("FINAL RECESS"));
                rire.selection("input").named(im.get("RECESS PRE CUTS"));
                rire.selection("tool").named(im.get("INNER RECESS CUTTER"));

                String sie1Label = "sel inner excess 1";
                GeomFeature sie1 = model
                    .geom(id)
                    .create(im.next("ballsel", sie1Label), "BallSelection");
                sie1.label(sie1Label);
                sie1.set("posx", "((R_in+Rect_recess)/2)*cos(Rotation_angle)");
                sie1.set("posy", "((R_in+Rect_recess)/2)*sin(Rotation_angle)");
                sie1.set("posz", "Center");
                sie1.set("r", 1);
                sie1.set("contributeto", im.get("SEL INNER EXCESS RECESS"));
                sie1.set("selkeep", false);

                String soe1Label = "sel outer excess 1";
                GeomFeature soe1 = model
                    .geom(id)
                    .create(im.next("ballsel", soe1Label), "BallSelection");
                soe1.label(soe1Label);
                soe1.set("posx", "((R_in+2*R_in)/2)*cos(Rotation_angle)");
                soe1.set("posy", "((R_in+2*R_in)/2)*sin(Rotation_angle)");
                soe1.set("posz", "Center");
                soe1.set("r", 1);
                soe1.set("contributeto", im.get("SEL OUTER EXCESS RECESS"));
                soe1.set("selkeep", false);

                String dierLabel = "Delete Inner Excess Recess";
                GeomFeature dier = model.geom(id).create(im.next("del", dierLabel), "Delete");
                dier.label(dierLabel);
                dier.selection("input").init(3);
                dier.selection("input").named(im.get("SEL INNER EXCESS RECESS"));

                String doerLabel = "Delete Outer Excess Recess";
                GeomFeature doer = model.geom(id).create(im.next("del", doerLabel), "Delete");
                doer.label(doerLabel);
                doer.selection("input").init(3);
                doer.selection("input").named(im.get("SEL OUTER EXCESS RECESS"));

                model.geom(id).create(im.next("endif"), "EndIf");

                String srcsLabel = "src";
                GeomFeature srcs = model.geom(id).create(im.next("pt", srcsLabel), "Point");
                srcs.label(srcsLabel);
                srcs.set("contributeto", im.get("SRC"));
                srcs.set(
                    "p",
                    new String[] {
                        "(R_in+Rect_recess+(Rect_thk/2))*cos(Rotation_angle)",
                        "(R_in+Rect_recess+(Rect_thk/2))*sin(Rotation_angle)",
                        "Center",
                    }
                );

                model.geom(id).run();

                break;
            case "uContact_Primitive":
                model.geom(id).inputParam().set("Center", "10 [mm]");
                model.geom(id).inputParam().set("R_in", "100 [um]");
                model.geom(id).inputParam().set("U_tangent", "200 [um]");
                model.geom(id).inputParam().set("U_thk", "20 [um]");
                model.geom(id).inputParam().set("U_z", "100 [um]");
                model.geom(id).inputParam().set("U_recess", "10 [um]");

                im.labels =
                    new String[] {
                        "CONTACT XS", //0
                        "CONTACT FINAL",
                        "SRC",
                        "RECESS XS",
                        "RECESS FINAL",
                        "EXCESS", // 5
                        "EXCESS2",
                    };

                for (String cselUContactLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselUContactLabel), "CumulativeSelection")
                        .label(cselUContactLabel);
                }

                String ucontactxsLabel = "Contact XS";
                GeomFeature ucontactxs = model
                    .geom(id)
                    .create(im.next("wp", ucontactxsLabel), "WorkPlane");
                ucontactxs.label(ucontactxsLabel);
                ucontactxs.set("contributeto", im.get(im.labels[0]));
                ucontactxs.set("quickz", "Center-U_z/2");
                ucontactxs.set("unite", true);

                String inLineLabel = "INLINE";
                ucontactxs
                    .geom()
                    .selection()
                    .create(im.next("csel", inLineLabel), "CumulativeSelection");
                ucontactxs.geom().selection(im.get(inLineLabel)).label(inLineLabel);

                String inLineUnionLabel = "INLINE_UNION";
                ucontactxs
                    .geom()
                    .selection()
                    .create(im.next("csel", inLineUnionLabel), "CumulativeSelection");
                ucontactxs.geom().selection(im.get(inLineUnionLabel)).label(inLineUnionLabel);

                String outLineLabel = "OUTLINE";
                ucontactxs
                    .geom()
                    .selection()
                    .create(im.next("csel", outLineLabel), "CumulativeSelection");
                ucontactxs.geom().selection(im.get(outLineLabel)).label(outLineLabel);

                String wpucontactxsLabel = "wpCONTACT XS";
                ucontactxs
                    .geom()
                    .selection()
                    .create(im.next("csel", wpucontactxsLabel), "CumulativeSelection");
                ucontactxs.geom().selection(im.get(wpucontactxsLabel)).label(wpucontactxsLabel);

                String roundInlineLabel = "Round Inline 3";
                GeomFeature rIL = ucontactxs
                    .geom()
                    .create(im.next("c", roundInlineLabel), "Circle");
                rIL.label(roundInlineLabel);
                rIL.set("contributeto", im.get(inLineLabel));
                rIL.set("r", "R_in+U_recess");

                String rectInlineLabel = "Rect Inline";
                GeomFeature rectIL = ucontactxs
                    .geom()
                    .create(im.next("r", rectInlineLabel), "Rectangle");
                rectIL.label(rectInlineLabel);
                rectIL.set("contributeto", im.get(inLineLabel));
                rectIL.set("pos", new String[] { "U_tangent/2", "0" });
                rectIL.set("base", "center");
                rectIL.set("size", new String[] { "U_tangent", "2*(R_in+U_recess)" });

                String uInlinePLabel = "Union Inline Parts";
                GeomFeature uInline = ucontactxs
                    .geom()
                    .create(im.next("uni", uInlinePLabel), "Union");
                uInline.label(uInlinePLabel);
                uInline.set("contributeto", im.get(inLineUnionLabel));
                uInline.set("intbnd", false);
                uInline.selection("input").named(im.get(inLineLabel));

                String roLabel = "Round Outline";
                GeomFeature ro = ucontactxs.geom().create(im.next("c", roLabel), "Circle");
                ro.label(roLabel);
                ro.set("contributeto", im.get(outLineLabel));
                ro.set("r", "R_in+U_thk+U_recess");

                String rectoLabel = "Rect Outline";
                GeomFeature urect = ucontactxs.geom().create(im.next("r", rectoLabel), "Rectangle");
                urect.label(rectoLabel);
                urect.set("contributeto", im.get(outLineLabel));
                urect.set("pos", new String[] { "U_tangent/2", "0" });
                urect.set("base", "center");
                urect.set("size", new String[] { "U_tangent", "2*(R_in+U_thk+U_recess)" });

                String uOPLabel = "Union Outline Parts";
                GeomFeature uOP = ucontactxs.geom().create(im.next("uni", uOPLabel), "Union");
                uOP.label(uOPLabel);
                uOP.set("contributeto", im.get(inLineUnionLabel));
                uOP.set("intbnd", false);
                uOP.selection("input").named(im.get(outLineLabel));

                String diff2cxsLabel = "Diff to Contact XS";
                GeomFeature diff2cxs = ucontactxs
                    .geom()
                    .create(im.next("dif", diff2cxsLabel), "Difference");
                diff2cxs.label(diff2cxsLabel);
                diff2cxs.selection("input").named(im.get(outLineLabel));
                diff2cxs.selection("input2").named(im.get(inLineLabel));

                String ifucontactoverhangLabel = "If Contact Overhang";
                GeomFeature ifucontactoverhang = ucontactxs
                    .geom()
                    .create(im.next("if", ifucontactoverhangLabel), "If");
                ifucontactoverhang.set("condition", "R_in+U_thk+U_recess > U_tangent");
                ifucontactoverhang.label(ifucontactoverhangLabel);
                String endifucontactoverhangLabel = "EndIf Contact Overhang";
                im.next("endif", endifucontactoverhangLabel);

                String ballselexcessu1Label = "Ball Selection Excess Contact";
                GeomFeature ballselexcessu1 = model
                    .geom(id)
                    .create(im.next("ballsel", ballselexcessu1Label), "BallSelection");
                ballselexcessu1.label(ballselexcessu1Label);
                ballselexcessu1.set("entitydim", 2);
                ballselexcessu1.set("posx", "U_recess+R_in+0.5*U_thk");
                ballselexcessu1.set("posz", "Center-U_z/2");
                ballselexcessu1.set("r", 1);
                ballselexcessu1.set("contributeto", im.get(im.labels[5]));

                String deleteexcessu1Label = "Delete Excess Contact";
                GeomFeature deleteexcessu1 = model
                    .geom(id)
                    .create(im.next("del", deleteexcessu1Label), "Delete");
                deleteexcessu1.label(deleteexcessu1Label);
                deleteexcessu1.set("selresult", true);
                deleteexcessu1.set("selresultshow", "all");
                deleteexcessu1.selection("input").named(im.get(im.labels[5]));

                GeomFeature endifucontactoverhang = ucontactxs
                    .geom()
                    .create(im.get(endifucontactoverhangLabel), "EndIf");
                endifucontactoverhang.label(endifucontactoverhangLabel);

                String umcLabel = "Make Contact";
                GeomFeature umc = model.geom(id).create(im.next("ext", umcLabel), "Extrude");
                umc.label(umcLabel);
                umc.set("contributeto", im.get(im.labels[1]));
                umc.set("extrudefrom", "faces");
                umc.setIndex("distance", "U_z", 0);
                umc.selection("inputface").named(im.get(im.labels[0]));

                String usrcLabel = "Src";
                GeomFeature usrc = model.geom(id).create(im.next("pt", usrcLabel), "Point");
                usrc.label(usrcLabel);
                usrc.set("contributeto", im.get("SRC"));
                usrc.set("p", new String[] { "-R_in-(U_thk/2)-U_recess", "0", "Center" });

                String ifurecessLabel = "If uRecess";
                GeomFeature ifurecess = model.geom(id).create(im.next("if", ifurecessLabel), "If");
                ifurecess.label(ifurecessLabel);
                ifurecess.set("condition", "U_recess > 0");
                String endifurecessLabel = "EndIf uRecess";
                im.next("endif", endifurecessLabel);

                String urecessxsLabel = "Recess XS";
                GeomFeature urecessxs = model
                    .geom(id)
                    .create(im.next("wp", urecessxsLabel), "WorkPlane");
                urecessxs.label(urecessxsLabel);
                urecessxs.set("contributeto", im.get("RECESS XS"));
                urecessxs.set("quickz", "Center-U_z/2");
                urecessxs.set("unite", true);

                String inLineRecessLabel = "INLINE RECESS";
                urecessxs
                    .geom()
                    .selection()
                    .create(im.next("csel", inLineRecessLabel), "CumulativeSelection");
                urecessxs.geom().selection(im.get(inLineRecessLabel)).label(inLineRecessLabel);

                String inLineRecessUnionLabel = "INLINE_RECESS_UNION";
                urecessxs
                    .geom()
                    .selection()
                    .create(im.next("csel", inLineRecessUnionLabel), "CumulativeSelection");
                urecessxs
                    .geom()
                    .selection(im.get(inLineRecessUnionLabel))
                    .label(inLineRecessUnionLabel);

                String outLineRecessLabel = "OUTLINE RECESS";
                urecessxs
                    .geom()
                    .selection()
                    .create(im.next("csel", outLineRecessLabel), "CumulativeSelection");
                urecessxs.geom().selection(im.get(outLineRecessLabel)).label(outLineRecessLabel);

                String inLineRecessPlaneLabel = "INLINE RECESS PLANE";
                urecessxs
                    .geom()
                    .selection()
                    .create(im.next("csel", inLineRecessPlaneLabel), "CumulativeSelection");
                urecessxs
                    .geom()
                    .selection(im.get(inLineRecessPlaneLabel))
                    .label(inLineRecessPlaneLabel);

                String roundInlineRecessLabel = "Round Inline Recess";
                GeomFeature roundInlineRecess = urecessxs
                    .geom()
                    .create(im.next("c", roundInlineRecessLabel), "Circle");
                roundInlineRecess.label(roundInlineRecessLabel);
                roundInlineRecess.set("contributeto", im.get(inLineRecessLabel));
                roundInlineRecess.set("r", "R_in");

                String rectInLineRecessLabel = "Rect Inline Recess";
                GeomFeature rectInLineRecess = urecessxs
                    .geom()
                    .create(im.next("r", rectInLineRecessLabel), "Rectangle");
                rectInLineRecess.label(rectInLineRecessLabel);
                rectInLineRecess.set("contributeto", im.get(inLineRecessLabel));
                rectInLineRecess.set("pos", new String[] { "U_tangent/2", "0" });
                rectInLineRecess.set("base", "center");
                rectInLineRecess.set("size", new String[] { "U_tangent", "2*(R_in)" });

                String unionInLinePartsRecessLabel = "Union Inline Parts Recess";
                GeomFeature unionInLinePartsRecess = urecessxs
                    .geom()
                    .create(im.next("uni", unionInLinePartsRecessLabel), "Union");
                unionInLinePartsRecess.label(unionInLinePartsRecessLabel);
                unionInLinePartsRecess.set("contributeto", im.get(inLineRecessUnionLabel));
                unionInLinePartsRecess.set("intbnd", false);
                unionInLinePartsRecess.selection("input").named(im.get(inLineRecessLabel));

                String roundOutlineRecessLabel = "Round Outline Recess";
                GeomFeature roundOutlineRecess = urecessxs
                    .geom()
                    .create(im.next("c", roundOutlineRecessLabel), "Circle");
                roundOutlineRecess.label(roundOutlineRecessLabel);
                roundOutlineRecess.set("contributeto", im.get(outLineRecessLabel));
                roundOutlineRecess.set("r", "R_in+U_recess");

                String rectOutLineRecessLabel = "Rect Outline Recess";
                GeomFeature rectOutLineRecess = urecessxs
                    .geom()
                    .create(im.next("r", rectOutLineRecessLabel), "Rectangle");
                rectOutLineRecess.label(rectOutLineRecessLabel);
                rectOutLineRecess.set("contributeto", im.get(outLineRecessLabel));
                rectOutLineRecess.set("pos", new String[] { "U_tangent/2", "0" });
                rectOutLineRecess.set("base", "center");
                rectOutLineRecess.set("size", new String[] { "U_tangent", "2*(R_in+U_recess)" });

                String unionOutlinePartsRecessLabel = "Union Outline Parts Recess";
                GeomFeature unionOutlinePartsRecess = urecessxs
                    .geom()
                    .create(im.next("uni", unionOutlinePartsRecessLabel), "Union");
                unionOutlinePartsRecess.label(unionOutlinePartsRecessLabel);
                unionOutlinePartsRecess.set("contributeto", im.get(inLineRecessUnionLabel));
                unionOutlinePartsRecess.set("intbnd", false);
                unionOutlinePartsRecess.selection("input").named(im.get(outLineRecessLabel));

                String difftoRecessXSLabel = "Diff to Recess XS";
                GeomFeature difftoRecessXS = urecessxs
                    .geom()
                    .create(im.next("dif", difftoRecessXSLabel), "Difference");
                difftoRecessXS.label(difftoRecessXSLabel);
                difftoRecessXS.selection("input").named(im.get(outLineRecessLabel));
                difftoRecessXS.selection("input2").named(im.get(inLineRecessLabel));

                String ifurecessoverhangLabel = "If Recess Overhang";
                GeomFeature ifurecessoverhang = ucontactxs
                    .geom()
                    .create(im.next("if", ifurecessoverhangLabel), "If");
                ifurecessoverhang.set("condition", "R_in+U_recess > U_tangent");
                ifurecessoverhang.label(ifurecessoverhangLabel);

                String ballselexcessu2Label = "Ball Selection Excess Recess";
                GeomFeature ballselexcessu2 = model
                    .geom(id)
                    .create(im.next("ballsel", ballselexcessu2Label), "BallSelection");
                ballselexcessu2.label(ballselexcessu2Label);
                ballselexcessu2.set("entitydim", 2);
                ballselexcessu2.set("posx", "R_in+0.5*U_recess");
                ballselexcessu2.set("posz", "Center-U_z/2");
                ballselexcessu2.set("r", 1);
                ballselexcessu2.set("contributeto", im.get(im.labels[6]));

                String deleteexcessu2Label = "Delete Excess Recess";
                GeomFeature deleteexcessu2 = model
                    .geom(id)
                    .create(im.next("del", deleteexcessu2Label), "Delete");
                deleteexcessu2.label(deleteexcessu2Label);
                deleteexcessu2.set("selresult", true);
                deleteexcessu2.set("selresultshow", "all");
                deleteexcessu2.selection("input").named(im.get(im.labels[6]));

                String endifurecessoverhangLabel = "EndIf Recess Overhang";
                GeomFeature endifurecessoverhang = ucontactxs
                    .geom()
                    .create(im.next("endif", endifurecessoverhangLabel), "EndIf");
                endifurecessoverhang.label(endifurecessoverhangLabel);

                String makeRecessLabel = "Make Recess";
                GeomFeature makeRecess = model
                    .geom(id)
                    .create(im.next("ext", makeRecessLabel), "Extrude");
                makeRecess.label(makeRecessLabel);
                makeRecess.set("contributeto", im.get(im.labels[4]));
                makeRecess.set("extrudefrom", "faces");
                makeRecess.setIndex("distance", "U_z", 0);
                makeRecess.selection("inputface").named(im.get(im.labels[3]));

                GeomFeature endifurecess = model
                    .geom(id)
                    .create(im.get(endifurecessLabel), "EndIf");
                endifurecess.label(endifurecessLabel);

                model.geom(id).run();

                break;
            case "uCuff_Primitive":
                model.geom(id).inputParam().set("Center", "10 [mm]");
                model.geom(id).inputParam().set("R_in", "100 [um]");
                model.geom(id).inputParam().set("U_tangent", "200 [um]");
                model.geom(id).inputParam().set("R_out", "300 [um]");
                model.geom(id).inputParam().set("U_L", "4 [mm]");
                model.geom(id).inputParam().set("U_shift_x", "0 [mm]");
                model.geom(id).inputParam().set("U_shift_y", "0 [mm]");
                model.geom(id).inputParam().set("U_gap", "20 [um]");

                im.labels =
                    new String[] {
                        "CUFF XS", //0
                        "CUFF FINAL",
                    };

                for (String cselUCuffLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselUCuffLabel), "CumulativeSelection")
                        .label(cselUCuffLabel);
                }

                String ucCXSLabel = "Cuff XS";
                GeomFeature ucCXS = model.geom(id).create(im.next("wp", ucCXSLabel), "WorkPlane");
                ucCXS.label(ucCXSLabel);
                ucCXS.set("contributeto", im.get("CUFF XS"));
                ucCXS.set("quickz", "Center-U_L/2");
                ucCXS.set("unite", true);

                String ucGapLabel = "GAP";
                ucCXS.geom().selection().create(im.next("csel", ucGapLabel), "CumulativeSelection");
                ucCXS.geom().selection(im.get(ucGapLabel)).label(ucGapLabel);

                String ucInlineLabel = "INLINE";
                ucCXS
                    .geom()
                    .selection()
                    .create(im.next("csel", ucInlineLabel), "CumulativeSelection");
                ucCXS.geom().selection(im.get(ucInlineLabel)).label(ucInlineLabel);

                String ucInlineUnion = "INLINE_UNION";
                ucCXS
                    .geom()
                    .selection()
                    .create(im.next("csel", ucInlineUnion), "CumulativeSelection");
                ucCXS.geom().selection(im.get(ucInlineUnion)).label(ucInlineUnion);

                String ucOutlineLabel = "OUTLINE";
                ucCXS
                    .geom()
                    .selection()
                    .create(im.next("csel", ucOutlineLabel), "CumulativeSelection");
                ucCXS.geom().selection(im.get(ucOutlineLabel)).label(ucOutlineLabel);

                String ucOutlineCuffLabel = "OUTLINE_CUFF";
                ucCXS
                    .geom()
                    .selection()
                    .create(im.next("csel", ucOutlineCuffLabel), "CumulativeSelection");
                ucCXS.geom().selection(im.get(ucOutlineCuffLabel)).label(ucOutlineCuffLabel);

                String uCuffXSLabel = "CUFF XS WP";
                ucCXS
                    .geom()
                    .selection()
                    .create(im.next("csel", uCuffXSLabel), "CumulativeSelection");
                ucCXS.geom().selection(im.get(uCuffXSLabel)).label(uCuffXSLabel);

                String ucCircleInlineLabel = "Round Inline";
                GeomFeature ucCircleInline = ucCXS
                    .geom()
                    .create(im.next("c", ucCircleInlineLabel), "Circle");
                ucCircleInline.label(ucCircleInlineLabel);
                ucCircleInline.set("contributeto", im.get(ucInlineLabel));
                ucCircleInline.set("r", "R_in");

                String ucRectInlineLabel = "Rect Inline";
                GeomFeature ucRectInline = ucCXS
                    .geom()
                    .create(im.next("r", ucRectInlineLabel), "Rectangle");
                ucRectInline.label(ucRectInlineLabel);
                ucRectInline.set("contributeto", im.get(ucInlineLabel));
                ucRectInline.set("pos", new String[] { "0", "-R_in" });
                ucRectInline.set("base", "corner");
                ucRectInline.set("size", new String[] { "U_tangent", "2*R_in" });

                String ucUnionInlineLabel = "Union Inline Parts";
                GeomFeature ucUnionInline = ucCXS
                    .geom()
                    .create(im.next("uni", ucUnionInlineLabel), "Union");
                ucUnionInline.label(ucUnionInlineLabel);
                ucUnionInline.set("contributeto", im.get("INLINE_UNION"));
                ucUnionInline.set("intbnd", false);
                ucUnionInline.selection("input").named(im.get("INLINE"));

                String ucCircleOutlineLabel = "Cuff Outline";
                GeomFeature ucCircleOutline = ucCXS
                    .geom()
                    .create(im.next("c", ucCircleOutlineLabel), "Circle");
                ucCircleOutline.label(ucCircleOutlineLabel);
                ucCircleOutline.set("contributeto", im.get(ucOutlineCuffLabel));
                ucCircleOutline.set("r", "R_out");
                ucCircleOutline.set("pos", new String[] { "U_shift_x", "-1*U_shift_y" });

                String ifucGapLabel = "If Gap";
                GeomFeature ifucGap = ucCXS.geom().create(im.next("if", ifucGapLabel), "If");
                ifucGap.label(ifucGapLabel);
                ifucGap.set("condition", "U_gap > 0");

                String ucGapRectLabel = "Gap";
                GeomFeature ucGapRect = ucCXS
                    .geom()
                    .create(im.next("r", ucGapRectLabel), "Rectangle");
                ucGapRect.label(ucGapRectLabel);
                ucGapRect.set("contributeto", im.get(ucGapLabel));
                ucGapRect.set("pos", new String[] { "R_out+U_shift_x", "0" });
                ucGapRect.set("base", "center");
                ucGapRect.set("size", new String[] { "2*(R_out+U_shift_x)", "U_gap" });

                String unionILwGapLabel = "Union Inline with Gap";
                GeomFeature unionILwGap = ucCXS
                    .geom()
                    .create(im.next("uni", unionILwGapLabel), "Union");
                unionILwGap.label(unionILwGapLabel);
                unionILwGap.set("intbnd", false);
                unionILwGap
                    .selection("input")
                    .set(im.get(ucGapRectLabel), im.get(ucUnionInlineLabel));

                String diffucXSLabel = "Diff to Cuff XS";
                GeomFeature diffucXS = ucCXS
                    .geom()
                    .create(im.next("dif", diffucXSLabel), "Difference");
                diffucXS.label(diffucXSLabel);
                diffucXS.set("contributeto", im.get(uCuffXSLabel));
                diffucXS.selection("input").named(im.get(ucOutlineCuffLabel));
                diffucXS.selection("input2").named(im.get(ucInlineUnion));

                String elseNoGapLabel = "Else No Gap";
                GeomFeature elseNoGap = ucCXS
                    .geom()
                    .create(im.next("else", elseNoGapLabel), "Else");
                elseNoGap.label(elseNoGapLabel);

                String diffCuffXSnoGapLabel = "Diff to Cuff XS No Gap";
                GeomFeature diffCuffXSnoGap = ucCXS
                    .geom()
                    .create(im.next("dif", diffCuffXSnoGapLabel), "Difference");
                diffCuffXSnoGap.label(diffCuffXSnoGapLabel);
                diffCuffXSnoGap.set("contributeto", im.get(uCuffXSLabel));
                diffCuffXSnoGap.selection("input").named(im.get(ucOutlineCuffLabel));
                diffCuffXSnoGap.selection("input2").named(im.get(ucInlineUnion));

                String enduIfGapLabel = "End IF Gap";
                GeomFeature enduIfGap = ucCXS
                    .geom()
                    .create(im.next("endif", enduIfGapLabel), "EndIf");
                enduIfGap.label(enduIfGapLabel);

                String ucExtLabel = "Make Cuff";
                GeomFeature ucExt = model.geom(id).create(im.next("ext", ucExtLabel), "Extrude");
                ucExt.label(ucExtLabel);
                ucExt.set("contributeto", im.get("CUFF FINAL"));
                ucExt.setIndex("distance", "U_L", 0);
                ucExt.selection("input").named(im.get("CUFF XS"));

                model.geom(id).run();

                break;
            case "uCuffFill_Primitive":
                model.geom(id).inputParam().set("Center", "10 [mm]");
                model.geom(id).inputParam().set("R_in", "100 [um]");
                model.geom(id).inputParam().set("U_tangent", "200 [um]");
                model.geom(id).inputParam().set("L", "4 [mm]");

                im.labels =
                    new String[] {
                        "Fill FINAL", //0
                    };

                for (String cselUCuffFillLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselUCuffFillLabel), "CumulativeSelection")
                        .label(cselUCuffFillLabel);
                }

                String ufCXLabel = "FILL XS";
                GeomFeature ufCX = model.geom(id).create(im.next("wp", ufCXLabel), "WorkPlane");
                ufCX.label(ufCXLabel);
                ufCX.set("quickz", "Center-L/2");
                ufCX.set("unite", true);

                String ufInlineLabel = "INLINE";
                ufCX
                    .geom()
                    .selection()
                    .create(im.next("csel", ufInlineLabel), "CumulativeSelection");
                ufCX.geom().selection(im.get(ufInlineLabel)).label(ufInlineLabel);

                String ufInlineUnion = "INLINE_UNION";
                ufCX
                    .geom()
                    .selection()
                    .create(im.next("csel", ufInlineUnion), "CumulativeSelection");
                ufCX.geom().selection(im.get(ufInlineUnion)).label(ufInlineUnion);

                String ufCircleInlineLabel = "Round Inline 2";
                GeomFeature ufCircleInline = ufCX
                    .geom()
                    .create(im.next("c", ufCircleInlineLabel), "Circle");
                ufCircleInline.label(ufCircleInlineLabel);
                ufCircleInline.set("contributeto", im.get(ufInlineLabel));
                ufCircleInline.set("r", "R_in");

                String ufRectInlineLabel = "Rect Inline";
                GeomFeature ufRectInline = ufCX
                    .geom()
                    .create(im.next("r", ufRectInlineLabel), "Rectangle");
                ufRectInline.label(ufRectInlineLabel);
                ufRectInline.set("contributeto", im.get(ufInlineLabel));
                ufRectInline.set("pos", new String[] { "U_tangent/2", "0" });
                ufRectInline.set("base", "center");
                ufRectInline.set("size", new String[] { "U_tangent", "2*R_in" });

                String ufUnionInlineLabel = "Union Inline Parts";
                GeomFeature ufUnionInline = ufCX
                    .geom()
                    .create(im.next("uni", ufUnionInlineLabel), "Union");
                ufUnionInline.set("intbnd", false);
                ufUnionInline.label(ufUnionInlineLabel);
                ufUnionInline.set("contributeto", im.get(ufInlineUnion));
                ufUnionInline.selection("input").named(im.get(ufInlineLabel));

                String ufExtLabel = "Make uFill";
                GeomFeature ufExt = model.geom(id).create(im.next("ext", ufExtLabel), "Extrude");
                ufExt.label(ufExtLabel);
                ufExt.set("contributeto", im.get("Fill FINAL"));
                ufExt.setIndex("distance", "L", 0);
                ufExt.selection("input").set(im.get(ufCXLabel));
                model.geom(id).run();

                break;
            case "CuffFill_Primitive":
                model.geom(id).inputParam().set("Radius", "0.5 [mm]");
                model.geom(id).inputParam().set("Thk", "100 [um]");
                model.geom(id).inputParam().set("L", "2.5 [mm]");
                model.geom(id).inputParam().set("Center", "0");
                model.geom(id).inputParam().set("x_shift", "0");
                model.geom(id).inputParam().set("y_shift", "0");

                im.labels =
                    new String[] {
                        "CUFF FILL FINAL", //0
                    };

                for (String cselCuffFillLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselCuffFillLabel), "CumulativeSelection")
                        .label(cselCuffFillLabel);
                }

                String cuffFillLabel = "Cuff Fill";
                GeomFeature cf = model.geom(id).create(im.next("cyl", cuffFillLabel), "Cylinder");
                cf.label(cuffFillLabel);
                cf.set("contributeto", im.get("CUFF FILL FINAL"));
                cf.set("pos", new String[] { "x_shift", "y_shift", "Center-(L/2)" });
                cf.set("r", "Radius");
                cf.set("h", "L");

                model.geom(id).run();

                break;
            case "uCuffTrap_Primitive":
                model.geom(id).lengthUnit("um");

                model.geom(id).inputParam().set("R_in", "70 [um]");
                model.geom(id).inputParam().set("Ut_tangent", "100 [um]");
                model.geom(id).inputParam().set("Rt_out", "150 [um]");
                model.geom(id).inputParam().set("Ut_shift_x", "0 [um]");
                model.geom(id).inputParam().set("Ut_shift_y", "0 [um]");
                model.geom(id).inputParam().set("Ut_gap", "10 [um]");
                model.geom(id).inputParam().set("Center", "0 [um]");
                model.geom(id).inputParam().set("Ut_L", "4 [mm]");
                model.geom(id).inputParam().set("Ut_trap_base", "200 [um]");

                im.labels =
                    new String[] {
                        "CUFF XS Trap", //0
                        "CUFF FINAL TRAP",
                    };

                for (String cseluCuffTrapLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cseluCuffTrapLabel), "CumulativeSelection")
                        .label(cseluCuffTrapLabel);
                }

                String cuffxstrapLabel = "Cuff XS_trap";
                GeomFeature cuffxstrap = model
                    .geom(id)
                    .create(im.next("wp", cuffxstrapLabel), "WorkPlane");
                cuffxstrap.label(cuffxstrapLabel);
                cuffxstrap.set("contributeto", "csel1");
                cuffxstrap.set("quickz", "Center-Ut_L/2");
                cuffxstrap.set("unite", true);

                String utOutlineCuffLabel = "OUTLINE_CUFF_TRAP";
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", utOutlineCuffLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection(im.get(utOutlineCuffLabel))
                    .label(utOutlineCuffLabel);

                String utInlineUnionLabel = "INLINE_TRAP_UNION";
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", utInlineUnionLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection(im.get(utInlineUnionLabel))
                    .label(utInlineUnionLabel);

                String utInlineLabel = "INLINE_TRAP";
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", utInlineLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection(im.get(utInlineLabel))
                    .label(utInlineLabel);

                String utCuffXSLabel = "CUFF XS TRAP";
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", utCuffXSLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection(im.get(utCuffXSLabel))
                    .label(utCuffXSLabel);

                String utGapTrapLabel = "GAP_TRAP";
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", utGapTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .selection(im.get(utGapTrapLabel))
                    .label(utGapTrapLabel);

                String roundInlineTrapLabel = "Round Inline Trap";
                GeomFeature roundInlineTrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("c", roundInlineTrapLabel), "Circle");
                roundInlineTrap.label(roundInlineTrapLabel);
                roundInlineTrap.set("contributeto", im.get(utInlineLabel));
                roundInlineTrap.set("r", "R_in");

                String trapInlineLabel = "Trap Inline";
                GeomFeature trapInline = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("pol", trapInlineLabel), "Polygon");
                trapInline.label(trapInlineLabel);
                trapInline.set("contributeto", im.get(utInlineLabel));
                trapInline.set("source", "table");
                trapInline.set(
                    "table",
                    new String[][] {
                        { "0", "R_in" },
                        { "Ut_tangent", "Ut_trap_base/2" },
                        { "Ut_tangent", "-Ut_trap_base/2" },
                        { "0", "-R_in" },
                    }
                );

                String unionInlinePartsLabel = "Union Inline Parts";
                GeomFeature unionInlineParts = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("uni", unionInlinePartsLabel), "Union");
                unionInlineParts.label(unionInlinePartsLabel);
                unionInlineParts.set("contributeto", im.get(utInlineUnionLabel));
                unionInlineParts.set("intbnd", false);
                unionInlineParts.selection("input").named(im.get(utInlineLabel));

                String cuffOutlineTrapLabel = "Cuff Outline Trap2";
                GeomFeature cuffOutlineTrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("c", cuffOutlineTrapLabel), "Circle");
                cuffOutlineTrap.label(cuffOutlineTrapLabel);
                cuffOutlineTrap.set("contributeto", im.get(utOutlineCuffLabel));
                cuffOutlineTrap.set("pos", new String[] { "Ut_shift_x", "Ut_shift_y" });
                cuffOutlineTrap.set("r", "Rt_out");

                String ifgaptrapLabel = "If Gap Trap";
                GeomFeature ifgaptrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("if", ifgaptrapLabel), "If");
                ifgaptrap.label(ifgaptrapLabel);
                ifgaptrap.set("condition", "Ut_gap > 0");

                String gapTrapLabel = "Gap Trap";
                GeomFeature gapTrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("r", gapTrapLabel), "Rectangle");
                gapTrap.label(gapTrapLabel);
                gapTrap.set("contributeto", im.get(utGapTrapLabel));
                gapTrap.set("pos", new String[] { "Rt_out+Ut_shift_x", "0" });
                gapTrap.set("base", "center");
                gapTrap.set("size", new String[] { "2*(Rt_out+Ut_shift_x)", "Ut_gap" });

                String unionInlineTrapwGapLabel = "Union Inline Trap with Gap";
                GeomFeature unionInlineTrapwGap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("uni", unionInlineTrapwGapLabel), "Union");
                unionInlineTrapwGap.label(unionInlineTrapwGapLabel);
                unionInlineTrapwGap.set("intbnd", false);
                unionInlineTrapwGap
                    .selection("input")
                    .set(im.get(gapTrapLabel), im.get(unionInlinePartsLabel));

                String difftoCuffXSTrapLabel = "Diff to Cuff XS Trap";
                GeomFeature difftoCuffXSTrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("dif", difftoCuffXSTrapLabel), "Difference");
                difftoCuffXSTrap.label(difftoCuffXSTrapLabel);
                difftoCuffXSTrap.set("contributeto", im.get(utCuffXSLabel));
                difftoCuffXSTrap.selection("input").named(im.get(utOutlineCuffLabel));
                difftoCuffXSTrap.selection("input2").named(im.get(utInlineUnionLabel));

                String elseNoGapTrapLabel = "Else No Gap Trap";
                GeomFeature elseNoGapTrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("else", elseNoGapTrapLabel), "Else");
                elseNoGapTrap.label(elseNoGapTrapLabel);

                String difftoCuffXSNoGapTrapLabel = "Diff to Cuff XS No Gap Trap";
                GeomFeature difftoCuffXSNoGapTrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("dif", difftoCuffXSNoGapTrapLabel), "Difference");
                difftoCuffXSNoGapTrap.label(difftoCuffXSNoGapTrapLabel);
                difftoCuffXSNoGapTrap.set("contributeto", im.get(utCuffXSLabel));
                difftoCuffXSNoGapTrap.selection("input").named(im.get(utOutlineCuffLabel));
                difftoCuffXSNoGapTrap.selection("input2").named(im.get(utInlineUnionLabel));

                String endifTrapLabel = "End If Trap";
                GeomFeature endifTrap = model
                    .geom(id)
                    .feature(im.get(cuffxstrapLabel))
                    .geom()
                    .create(im.next("endif", endifTrapLabel), "EndIf");
                endifTrap.label(endifTrapLabel);

                String makeCuffTrapLabel = "Make Cuff_trap";
                GeomFeature makeCuffTrap = model
                    .geom(id)
                    .create(im.next("ext", makeCuffTrapLabel), "Extrude");
                makeCuffTrap.label(makeCuffTrapLabel);
                makeCuffTrap.set("contributeto", im.get("CUFF FINAL TRAP"));
                makeCuffTrap.setIndex("distance", "Ut_L", 0);
                makeCuffTrap.selection("input").set(im.get(cuffxstrapLabel));

                model.geom(id).run();

                break;
            case "uContactTrap_Primitive":
                model.geom(id).label("uContactTrap_Primitive");

                model.geom(id).lengthUnit("um");

                model.geom(id).inputParam().set("Center", "0 [mm]");
                model.geom(id).inputParam().set("R_in", "100 [um]");
                model.geom(id).inputParam().set("Ut_thk", "50 [um]");
                model.geom(id).inputParam().set("Ut_tangent", "200 [um]");
                model.geom(id).inputParam().set("Ut_recess", "10 [um]");
                model.geom(id).inputParam().set("Ut_z", "100 [um]");
                model.geom(id).inputParam().set("Ut_trap_base", "250 [um]");

                im.labels =
                    new String[] {
                        "RECESS XS Trap", //0
                        "CONTACT XS Trap",
                        "RECESS FINAL TRAP",
                        "CONTACT FINAL TRAP",
                        "SRC FINAL",
                    };

                for (String cseluContactTrapLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cseluContactTrapLabel), "CumulativeSelection")
                        .label(cseluContactTrapLabel);
                }

                String contactXSTrapLabel = "Contact XS Trap";
                GeomFeature contactXSTrap = model
                    .geom(id)
                    .create(im.next("wp", contactXSTrapLabel), "WorkPlane");
                contactXSTrap.label(contactXSTrapLabel);
                contactXSTrap.set("contributeto", im.get("CONTACT XS Trap"));
                contactXSTrap.set("quickz", "Center-Ut_z/2");
                contactXSTrap.set("unite", true);

                String outLineUnionTrapLabel = "OUTLINE_UNION";
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", outLineUnionTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection(im.get(outLineUnionTrapLabel))
                    .label(outLineUnionTrapLabel);

                String inlineTrapLabel = "INLINE";
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", inlineTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection(im.get(inlineTrapLabel))
                    .label(inlineTrapLabel);

                String inlineUnionTrapLabel = "INLINE_UNION";
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", inlineUnionTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection(im.get(inlineUnionTrapLabel))
                    .label(inlineUnionTrapLabel);

                String outlineTrapLabel = "OUTLINE TRAP";
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", outlineTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection(im.get(outlineTrapLabel))
                    .label(outlineTrapLabel);

                String wpContactXSTrapLabel = "wpCONTACT XS";
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", wpContactXSTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .selection(im.get(wpContactXSTrapLabel))
                    .label(wpContactXSTrapLabel);

                String roundOutlineContactTrapLabel = "Round Outline Contact Trap";
                GeomFeature roundOutlineContactTrap = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("c", roundOutlineContactTrapLabel), "Circle");
                roundOutlineContactTrap.label(roundOutlineContactTrapLabel);
                roundOutlineContactTrap.set("contributeto", im.get(outlineTrapLabel));
                roundOutlineContactTrap.set("r", "R_in+Ut_thk+Ut_recess");

                String trapOutlineContactLabel = "Trap Outline Contact";
                GeomFeature trapOutlineContact = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("pol", trapOutlineContactLabel), "Polygon");
                trapOutlineContact.label(trapOutlineContactLabel);
                trapOutlineContact.set("contributeto", im.get(outlineTrapLabel));
                trapOutlineContact.set("source", "table");
                trapOutlineContact.set(
                    "table",
                    new String[][] {
                        { "0", "R_in+Ut_thk+Ut_recess" },
                        { "Ut_tangent", "Ut_thk+Ut_recess+Ut_trap_base/2" },
                        { "Ut_tangent", "-Ut_thk-Ut_recess-Ut_trap_base/2" },
                        { "0", "-R_in-Ut_thk-Ut_recess" },
                    }
                );

                String unionOutlineTrapContactPartsLabel = "Union Outline Trap Contact Parts";
                GeomFeature unionOutlineTrapContactParts = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("uni", unionOutlineTrapContactPartsLabel), "Union");
                unionOutlineTrapContactParts.label(unionOutlineTrapContactPartsLabel);
                unionOutlineTrapContactParts.set("contributeto", im.get(outLineUnionTrapLabel));
                unionOutlineTrapContactParts.set("intbnd", false);
                unionOutlineTrapContactParts.selection("input").named(im.get(outlineTrapLabel));

                String roundInlineContactTrapLabel = "Round Inline Contact Trap";
                GeomFeature roundInlineContactTrap = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("c", roundInlineContactTrapLabel), "Circle");
                roundInlineContactTrap.label(roundInlineContactTrapLabel);
                roundInlineContactTrap.set("contributeto", im.get(inlineTrapLabel));
                roundInlineContactTrap.set("r", "R_in+Ut_recess");

                String trapContactInlineLabel = "Trap Contact Inline";
                GeomFeature trapContactInline = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("pol", trapContactInlineLabel), "Polygon");
                trapContactInline.label(trapContactInlineLabel);
                trapContactInline.set("contributeto", im.get(inlineTrapLabel));
                trapContactInline.set("source", "table");
                trapContactInline.set(
                    "table",
                    new String[][] {
                        { "0", "R_in+Ut_recess" },
                        { "Ut_tangent", "Ut_recess+Ut_trap_base/2" },
                        { "Ut_tangent", "-Ut_recess-Ut_trap_base/2" },
                        { "0", "-R_in-Ut_recess" },
                    }
                );

                String unionInlineContactTrapLabel = "Union Inline Contact Trap";
                GeomFeature unionInlineContactTrap = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("uni", unionInlineContactTrapLabel), "Union");
                unionInlineContactTrap.label(unionInlineContactTrapLabel);
                unionInlineContactTrap.set("contributeto", im.get(inlineUnionTrapLabel));
                unionInlineContactTrap.set("intbnd", false);
                unionInlineContactTrap.selection("input").named(im.get(outlineTrapLabel));

                String difftoContactXSTrapLabel = "Diff to Contact XS";
                GeomFeature difftoContactXSTrap = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("dif", difftoContactXSTrapLabel), "Difference");
                difftoContactXSTrap.label(difftoContactXSTrapLabel);
                difftoContactXSTrap.selection("input").named(im.get(outlineTrapLabel));
                difftoContactXSTrap.selection("input2").named(im.get(inlineTrapLabel));

                String ifNeedDelContactPieceLabel = "if need to delete contact piece";
                GeomFeature ifNeedDelContactPiece = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("if", ifNeedDelContactPieceLabel), "If");
                ifNeedDelContactPiece.label(ifNeedDelContactPieceLabel);
                ifNeedDelContactPiece.set("condition", "(R_in + Ut_recess + Ut_thk) > Ut_tangent");

                String delContactPiece1Label = "delete contact piece 1";
                GeomFeature delContactPiece1 = model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("del", delContactPiece1Label), "Delete");
                delContactPiece1.label(delContactPiece1Label);
                delContactPiece1.selection("input").init(2);
                delContactPiece1
                    .selection("input")
                    .set(im.get(difftoContactXSTrapLabel) + "(1)", 2);
                model
                    .geom(id)
                    .feature(im.get(contactXSTrapLabel))
                    .geom()
                    .create(im.next("endif"), "EndIf");

                String makeContactTrapLabel = "Make Contact Trap";
                GeomFeature makeContactTrap = model
                    .geom(id)
                    .create(im.next("ext", makeContactTrapLabel), "Extrude");
                makeContactTrap.label(makeContactTrapLabel);
                makeContactTrap.set("contributeto", im.get("CONTACT FINAL TRAP"));
                makeContactTrap.setIndex("distance", "Ut_z", 0);
                makeContactTrap.selection("input").named(im.get("CONTACT XS Trap"));

                String src1TrapLabel = "Src 1";
                GeomFeature src1Trap = model.geom(id).create(im.next("pt", src1TrapLabel), "Point");
                src1Trap.label(src1TrapLabel);
                src1Trap.set("p", new String[] { "-R_in-(Ut_thk/2)-Ut_recess", "0", "Center" });
                src1Trap.set("contributeto", im.get("SRC FINAL"));

                String ifRecessTrapLabel = "If Recess Trap";
                GeomFeature ifRecessTrap = model
                    .geom(id)
                    .create(im.next("if", ifRecessTrapLabel), "If");
                ifRecessTrap.label(ifRecessTrapLabel);
                ifRecessTrap.set("condition", "Ut_recess > 0");

                String recessXSTrapLabel = "Recess XS Trap WP";
                GeomFeature recessXSTrap = model
                    .geom(id)
                    .create(im.next("wp", recessXSTrapLabel), "WorkPlane");
                recessXSTrap.label(recessXSTrapLabel);

                recessXSTrap.set("contributeto", im.get("RECESS XS Trap"));
                recessXSTrap.set("quickz", "Center-Ut_z/2");
                recessXSTrap.set("unite", true);

                String inlineRecessTrapLabel = "INLINE RECESS";
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", inlineRecessTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection(im.get(inlineRecessTrapLabel))
                    .label(inlineRecessTrapLabel);

                String inlinerecessUnionTrapLabel = "INLINE_RECESS_UNION";
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", inlinerecessUnionTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection(im.get(inlinerecessUnionTrapLabel))
                    .label(inlinerecessUnionTrapLabel);

                String outlineRecessTrapLabel = "OUTLINE";
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", outlineRecessTrapLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection(im.get(outlineRecessTrapLabel))
                    .label(outlineRecessTrapLabel);

                String outlineRecessTrapUnionLabel = "OUTLINE RECESS UNION";
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection()
                    .create(im.next("csel", outlineRecessTrapUnionLabel), "CumulativeSelection");
                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .selection(im.get(outlineRecessTrapUnionLabel))
                    .label(outlineRecessTrapUnionLabel);

                String rorTrapLabel = "Round Outline Recess Trap";
                GeomFeature rorTrap = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("c", rorTrapLabel), "Circle");
                rorTrap.label(rorTrapLabel);
                rorTrap.set("contributeto", im.get(outlineRecessTrapLabel));
                rorTrap.set("r", "R_in+Ut_recess");

                String trapOutlineRecessLabel = "Trap Outline Recess";
                GeomFeature trapOutlineRecess = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("pol", trapOutlineRecessLabel), "Polygon");
                trapOutlineRecess.label(trapOutlineRecessLabel);
                trapOutlineRecess.set("contributeto", im.get(outlineRecessTrapLabel));
                trapOutlineRecess.set("source", "table");
                trapOutlineRecess.set(
                    "table",
                    new String[][] {
                        { "0", "R_in+Ut_recess" },
                        { "Ut_tangent", "Ut_recess+Ut_trap_base/2" },
                        { "Ut_tangent", "-Ut_recess-Ut_trap_base/2" },
                        { "0", "-R_in-Ut_recess" },
                    }
                );

                String unionOutlineTrapRecessLabel = "Union Outline Trap Recess Parts";
                GeomFeature unionOutlineTrapRecess = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("uni", unionOutlineTrapRecessLabel), "Union");
                unionOutlineTrapRecess.label(unionOutlineTrapRecessLabel);
                unionOutlineTrapRecess.set("contributeto", im.get(outlineRecessTrapUnionLabel));
                unionOutlineTrapRecess.set("intbnd", false);
                unionOutlineTrapRecess.selection("input").named(im.get(outlineRecessTrapLabel));

                String roundInlineRecessTrapLabel2 = "Round Inline Recess Trap2";
                GeomFeature roundInlineRecessTrap = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("c", roundInlineRecessTrapLabel2), "Circle");
                roundInlineRecessTrap.label(roundInlineRecessTrapLabel2);
                roundInlineRecessTrap.set("contributeto", im.get(inlineRecessTrapLabel));
                roundInlineRecessTrap.set("r", "R_in");

                String trapInlineRecessLabel = "Trap Inline Recess";
                GeomFeature trapInlineRecess = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("pol", trapInlineRecessLabel), "Polygon");
                trapInlineRecess.label(trapInlineRecessLabel);
                trapInlineRecess.set("contributeto", im.get(inlineRecessTrapLabel));
                trapInlineRecess.set("source", "table");
                trapInlineRecess.set(
                    "table",
                    new String[][] {
                        { "0", "R_in" },
                        { "Ut_tangent", "Ut_trap_base/2" },
                        { "Ut_tangent", "-Ut_trap_base/2" },
                        { "0", "-R_in" },
                    }
                );

                String unionInlineRecessTrapLabel = "Union Inline Recess Trap";
                GeomFeature unionInlineRecessTrap = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("uni", unionInlineRecessTrapLabel), "Union");
                unionInlineRecessTrap.label(unionInlineRecessTrapLabel);
                unionInlineRecessTrap.set("contributeto", im.get(inlinerecessUnionTrapLabel));
                unionInlineRecessTrap.set("intbnd", false);
                unionInlineRecessTrap.selection("input").named(im.get(inlineRecessTrapLabel));

                String difftoRecessXSTrapLabel = "Diff to Recess XS";
                GeomFeature difftoRecessXSTrap = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("dif", difftoRecessXSTrapLabel), "Difference");
                difftoRecessXSTrap.label(difftoRecessXSTrapLabel);
                difftoRecessXSTrap.selection("input").named(im.get(outlineRecessTrapUnionLabel));
                difftoRecessXSTrap.selection("input2").named(im.get(inlinerecessUnionTrapLabel));

                String ifNeedToDeleteRecessPieceLabel = "if need to delete recess piece";

                GeomFeature ifNeedToDeleteRecessPiece = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("if", ifNeedToDeleteRecessPieceLabel), "If");
                ifNeedToDeleteRecessPiece.label(ifNeedToDeleteRecessPieceLabel);
                ifNeedToDeleteRecessPiece.set("condition", "(R_in + Ut_recess) > Ut_tangent");

                String DeleteRecessPieceLabel = "delete recess piece 1";
                GeomFeature DeleteRecessPiece = model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("del", DeleteRecessPieceLabel), "Delete");
                DeleteRecessPiece.label(DeleteRecessPieceLabel);
                DeleteRecessPiece.selection("input").init(2);
                DeleteRecessPiece
                    .selection("input")
                    .set(im.get(difftoRecessXSTrapLabel) + "(1)", 2);

                model
                    .geom(id)
                    .feature(im.get(recessXSTrapLabel))
                    .geom()
                    .create(im.next("endif"), "EndIf");

                String makeRecessTrapLabel = "Make Recess";
                GeomFeature makeRecessTrap = model
                    .geom(id)
                    .create(im.next("ext", makeRecessTrapLabel), "Extrude");
                makeRecessTrap.label(makeRecessTrapLabel);
                makeRecessTrap.set("contributeto", im.get("RECESS FINAL TRAP"));
                makeRecessTrap.setIndex("distance", "Ut_z", 0);
                makeRecessTrap.selection("input").named(im.get("RECESS XS Trap"));

                String endifRecessTrapLabel = "end if recess Trap";
                GeomFeature endifRecessTrap = model
                    .geom(id)
                    .create(im.next("endif", endifRecessTrapLabel), "EndIf");
                endifRecessTrap.label(endifRecessTrapLabel);

                model.geom(id).run();

                break;
            case "ArleContact_Primitive":
                model.geom(id).label("ArleContact_Primitive");

                model.geom(id).inputParam().set("Gauge_AC", "600 [um]");
                model.geom(id).inputParam().set("Wrap_AC", "270 [deg]");
                model.geom(id).inputParam().set("R_in", "1.5 [mm]");
                model.geom(id).inputParam().set("L_AC", "2 [mm]");
                model.geom(id).inputParam().set("Center", "0 [mm]");

                im.labels =
                    new String[] {
                        "SEMI CIRC CONTACT", // 0
                        "Semi Sweep",
                        "ARLE CONTACT FINAL",
                        "SRC FINAL",
                    };

                for (String cselCarleContactLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselCarleContactLabel), "CumulativeSelection")
                        .label(cselCarleContactLabel);
                }

                String contactXS_AC_label = "Contact XS AC";
                GeomFeature contactXS_AC = model
                    .geom(id)
                    .create(im.next("wp", contactXS_AC_label), "WorkPlane");
                contactXS_AC.set("quickplane", "xz");
                contactXS_AC.set("unite", true);

                String circContactACLabel = "CIRC_CONTACT";
                contactXS_AC
                    .geom()
                    .selection()
                    .create(im.next("csel", circContactACLabel), "CumulativeSelection");
                contactXS_AC.geom().selection(im.get(circContactACLabel)).label(circContactACLabel);

                String circContactCutterLabel = "CIRC CONTACT CUTTER";
                contactXS_AC
                    .geom()
                    .selection()
                    .create(im.next("csel", circContactCutterLabel), "CumulativeSelection");
                contactXS_AC
                    .geom()
                    .selection(im.get(circContactCutterLabel))
                    .label(circContactCutterLabel);

                String circContactXSAC_label = "CIRC CONTACT XS";
                contactXS_AC
                    .geom()
                    .selection()
                    .create(im.next("csel", circContactXSAC_label), "CumulativeSelection");
                contactXS_AC
                    .geom()
                    .selection(im.get(circContactXSAC_label))
                    .label(circContactXSAC_label);

                String preSemiCircAC_label = "Pre Semi Circ Contact";
                GeomFeature preSemiCircAC = contactXS_AC
                    .geom()
                    .create(im.next("c", preSemiCircAC_label), "Circle");
                preSemiCircAC.label(preSemiCircAC_label);
                preSemiCircAC.set("contributeto", im.get(circContactACLabel));
                preSemiCircAC.set("pos", new String[] { "R_in", "Center-L_AC/2" });
                preSemiCircAC.set("r", "Gauge_AC/2");

                String preSemiCircACCutter_label = "Semi Circ Contact Cutter";
                GeomFeature preSemiCircACCutter = contactXS_AC
                    .geom()
                    .create(im.next("sq", preSemiCircACCutter_label), "Square");
                preSemiCircACCutter.label(preSemiCircACCutter_label);
                preSemiCircACCutter.set("contributeto", im.get(circContactCutterLabel));
                preSemiCircACCutter.set("pos", new String[] { "R_in-Gauge_AC/2", "Center-L_AC/2" });
                preSemiCircACCutter.set("base", "center");
                preSemiCircACCutter.set("size", "Gauge_AC");

                String makeSemiCircAC_label = "Make Semi Circ";
                GeomFeature makeSemiCircAC = contactXS_AC
                    .geom()
                    .create(im.next("dif", makeSemiCircAC_label), "Difference");
                makeSemiCircAC.label(makeSemiCircAC_label);
                makeSemiCircAC.set("contributeto", im.get(circContactXSAC_label));
                makeSemiCircAC.selection("input").named(im.get(circContactACLabel));
                makeSemiCircAC.selection("input2").named(im.get(circContactCutterLabel));

                String pcSemiCirc_ACLabel = "Sweep PC AC";
                GeomFeature pcSemiCirc_AC = model
                    .geom(id)
                    .create(im.next("pc", pcSemiCirc_ACLabel), "ParametricCurve");
                pcSemiCirc_AC.set("contributeto", im.get("Semi Sweep"));
                pcSemiCirc_AC.set("parmax", "Wrap_AC/360");
                pcSemiCirc_AC.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*((Gauge_AC/4)+R_in)",
                        "sin(2*pi*s)*((Gauge_AC/4)+R_in)",
                        "Center-(L_AC/2)+((360*L_AC)/Wrap_AC)*s",
                    }
                );

                String make_contactACLabel = "Make Contact AC";
                GeomFeature make_contactAC = model
                    .geom(id)
                    .create(im.next("swe", make_contactACLabel), "Sweep");

                make_contactAC.set("contributeto", im.get("ARLE CONTACT FINAL"));
                make_contactAC.set("crossfaces", true);
                make_contactAC.set("includefinal", false);
                make_contactAC.set("twistcomp", false);
                make_contactAC
                    .selection("face")
                    .named(im.get(contactXS_AC_label) + "_" + im.get(circContactXSAC_label));
                make_contactAC.selection("edge").named(im.get("Semi Sweep"));
                make_contactAC.selection("diredge").set(im.get(pcSemiCirc_ACLabel) + "(1)", 1);

                String src_pt_ACLabel = "AC_src_pt";
                GeomFeature src_pt_AC = model
                    .geom(id)
                    .create(im.next("pt", src_pt_ACLabel), "Point");
                src_pt_AC.set("contributeto", im.get("SRC FINAL"));
                src_pt_AC.set(
                    "p",
                    new String[] {
                        "((Gauge_AC/4)+R_in)*cos((Wrap_AC/(2))*2*pi)",
                        "((Gauge_AC/4)+R_in)*sin((Wrap_AC/(2))*2*pi)",
                        "Center-(L_AC/2)+((360*L_AC)/Wrap_AC)*(Wrap_AC/(360*2))",
                    }
                );

                model.geom(id).run();

                break;
            case "LivaNova_Primitive":
                mp.set("Center", "0 [mm]");
                mp.set("Thk_cuff", "610 [um]");
                mp.set("W_cuff", "1410 [um]");
                mp.set("R_in", "1109.4 [um]");
                mp.set("L_cuff", "3852.6 [um]");
                mp.set("Rev_insul", "2.2471");
                mp.set("Rev_cond", "0.84514");
                mp.set("Recess", "0 [mm]");
                mp.set("Thk_elec", "50 [um]");
                mp.set("W_elec", "775 [um]");
                mp.set("Rot", "0 [deg]");

                im.labels =
                    new String[] {
                        "SEL END P1", //0
                        "PC1",
                        "CUFF P1",
                        "RECESS P2", //3
                        "PC2",
                        "SRC FINAL",
                        "CUFF P2",
                        "SEL END P2",
                        "PC3",
                        "CONDUCTOR P2", //9
                        "CUFF P3",
                        "PC4",
                        "CUFF FINAL",
                    };

                for (String cselLNLabel : im.labels) {
                    model
                        .geom(id)
                        .selection()
                        .create(im.next("csel", cselLNLabel), "CumulativeSelection")
                        .label(cselLNLabel);
                }

                String LNhicsp1Label = "Helical Insulator Cross Section Part 1";
                GeomFeature LNhicsp1 = model
                    .geom(id)
                    .create(im.next("wp", LNhicsp1Label), "WorkPlane");
                LNhicsp1.label(LNhicsp1Label);
                LNhicsp1.set("quickplane", "xz");
                LNhicsp1.set("unite", true);

                String LNhicsLabel = "HELICAL INSULATOR CROSS SECTION PART 1";
                LNhicsp1
                    .geom()
                    .selection()
                    .create(im.next("csel", LNhicsLabel), "CumulativeSelection");
                LNhicsp1.geom().selection(im.get(LNhicsLabel)).label(LNhicsLabel);

                String LNhicxp1Label = "Helical Insulator Cross Section Part 1";
                LNhicsp1.geom().create("r1", "Rectangle");
                LNhicsp1.geom().feature("r1").label(LNhicxp1Label);
                LNhicsp1.geom().feature("r1").set("contributeto", im.get(LNhicsLabel));
                LNhicsp1
                    .geom()
                    .feature("r1")
                    .set("pos", new String[] { "R_in+0.5*Thk_cuff", "Center-0.5*L_cuff" });
                LNhicsp1.geom().feature("r1").set("base", "center");
                LNhicsp1.geom().feature("r1").set("size", new String[] { "Thk_cuff", "W_cuff" });

                String LNpcp1Label = "Parametric Curve Part 1";
                GeomFeature LNpc1 = model
                    .geom(id)
                    .create(im.next("pc", LNpcp1Label), "ParametricCurve");
                LNpc1.label(LNpcp1Label);
                LNpc1.set("contributeto", im.get(im.labels[1]));
                LNpc1.set("pos", new int[] { 0, 0, 0 });
                LNpc1.set("parmax", "(Rev_insul/2)-(Rev_cond/2)");
                LNpc1.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*(R_in)",
                        "sin(2*pi*s)*(R_in)",
                        "Center+(L_cuff)*(s/Rev_insul)-(L_cuff/2)",
                    }
                );

                String LNmcp1Label = "Make Cuff Part 1";
                GeomFeature LNmcp1 = model.geom(id).create(im.next("swe", LNmcp1Label), "Sweep");
                LNmcp1.label(LNmcp1Label);
                LNmcp1.set("contributeto", im.get(im.labels[2]));
                LNmcp1.set("keep", false);
                LNmcp1.set("includefinal", false);
                LNmcp1.set("twistcomp", false);

                LNmcp1.selection("face").named(im.get(LNhicsp1Label) + "_" + im.get(LNhicsLabel));
                LNmcp1.selection("edge").named(im.get(im.labels[1]));
                LNmcp1.selection("diredge").set(im.get(LNpcp1Label) + "(1)", 1);

                String LNsefp1Label = "Select End Face Part 1";
                GeomFeature LNsefp1 = model
                    .geom(id)
                    .create(im.next("ballsel", LNsefp1Label), "BallSelection");
                LNsefp1.set("entitydim", 2);
                LNsefp1.label(LNsefp1Label);
                LNsefp1.set("posx", "cos(2*pi*((Rev_insul/2)-(Rev_cond/2)))*(R_in+Thk_cuff/2)");
                LNsefp1.set("posy", "sin(2*pi*((Rev_insul/2)-(Rev_cond/2)))*(R_in+Thk_cuff/2)");
                LNsefp1.set(
                    "posz",
                    "Center+(L_cuff)*(((Rev_insul/2)-(Rev_cond/2))/Rev_insul)-(L_cuff/2)"
                );
                LNsefp1.set("r", 1);
                LNsefp1.set("contributeto", im.get(im.labels[0]));

                String LNhicsp2Label = "Helical Insulator Cross Section Part 2";
                GeomFeature LNhicsp2 = model
                    .geom(id)
                    .create(im.next("wp", LNhicsp2Label), "WorkPlane");
                LNhicsp2.label(LNhicsp2Label);
                LNhicsp2.set("planetype", "faceparallel");
                LNhicsp2.set("unite", true);
                LNhicsp2.selection("face").named(im.get(im.labels[0]));

                String LNhicxp2Label = "HELICAL INSULATOR CROSS SECTION PART 2";
                LNhicsp2
                    .geom()
                    .selection()
                    .create(im.next("csel", LNhicxp2Label), "CumulativeSelection");
                LNhicsp2.geom().selection(im.get(LNhicxp2Label)).label(LNhicxp2Label);
                LNhicsp2.geom().create("r1", "Rectangle");
                LNhicsp2.geom().feature("r1").label("Helical Insulator Cross Section Part 2");
                LNhicsp2.geom().feature("r1").set("contributeto", im.get(LNhicxp2Label));
                LNhicsp2.geom().feature("r1").set("pos", new int[] { 0, 0 });
                LNhicsp2.geom().feature("r1").set("base", "center");
                LNhicsp2.geom().feature("r1").set("size", new String[] { "Thk_cuff", "W_cuff" });

                String LNhccxp2Label = "Helical Conductor Cross Section Part 2";
                GeomFeature LNhccxp2 = model
                    .geom(id)
                    .create(im.next("wp", LNhccxp2Label), "WorkPlane");
                LNhccxp2.label(LNhccxp2Label);
                LNhccxp2.set("planetype", "faceparallel");
                LNhccxp2.set("unite", true);
                LNhccxp2.selection("face").named(im.get(im.labels[0]));

                String LNhccxp2wpresessLabel = "HELICAL CONDUCTOR CROSS SECTION PART 2 (recess)";
                LNhccxp2
                    .geom()
                    .selection()
                    .create(im.next("csel", LNhccxp2wpresessLabel), "CumulativeSelection");
                LNhccxp2
                    .geom()
                    .selection(im.get(LNhccxp2wpresessLabel))
                    .label(LNhccxp2wpresessLabel);

                String LNhcrxp2wpLabel = "HELICAL RECESS CROSS SECTION PART 2";
                LNhccxp2
                    .geom()
                    .selection()
                    .create(im.next("csel", LNhcrxp2wpLabel), "CumulativeSelection");
                LNhccxp2.geom().selection(im.get(LNhcrxp2wpLabel)).label(LNhcrxp2wpLabel);

                LNhccxp2.geom().create("if1", "If");
                LNhccxp2.geom().feature("if1").set("condition", "Recess>0");

                LNhccxp2.geom().create("r2", "Rectangle");
                LNhccxp2
                    .geom()
                    .feature("r2")
                    .label("Helical Conductor Cross Section Part 2 (recess)");
                LNhccxp2.geom().feature("r2").set("contributeto", im.get(LNhccxp2wpresessLabel));
                LNhccxp2
                    .geom()
                    .feature("r2")
                    .set("pos", new String[] { "Recess+(Thk_elec-Thk_cuff)/2", "0" });
                LNhccxp2.geom().feature("r2").set("base", "center");
                LNhccxp2.geom().feature("r2").set("size", new String[] { "Thk_elec", "W_elec" });

                LNhccxp2.geom().create("endif1", "EndIf");

                LNhccxp2.geom().create("if2", "If");
                LNhccxp2.geom().feature("if2").set("condition", "Recess==0");

                LNhccxp2.geom().create("r3", "Rectangle");
                LNhccxp2
                    .geom()
                    .feature("r3")
                    .label("Helical Conductor Cross Section Part 2 (no recess)");
                LNhccxp2.geom().feature("r3").set("contributeto", im.get(LNhccxp2wpresessLabel));
                LNhccxp2
                    .geom()
                    .feature("r3")
                    .set("pos", new String[] { "(Thk_elec-Thk_cuff)/2", "0" });
                LNhccxp2.geom().feature("r3").set("base", "center");
                LNhccxp2.geom().feature("r3").set("size", new String[] { "Thk_elec", "W_elec" });

                LNhccxp2.geom().create("endif2", "EndIf");

                String LNhrcxp2Label = "Helical Recess Cross Section Part 2";
                GeomFeature LNhrcxp2 = model
                    .geom(id)
                    .create(im.next("wp", LNhrcxp2Label), "WorkPlane");
                LNhrcxp2.label(LNhrcxp2Label);
                LNhrcxp2.set("planetype", "faceparallel");
                LNhrcxp2.set("unite", true);

                LNhrcxp2.selection("face").named("csel1");

                String LNhccsp2Label = "HELICAL CONDUCTOR CROSS SECTION PART 2";
                LNhrcxp2.geom().selection().create("csel1", "CumulativeSelection");
                LNhrcxp2.geom().selection("csel1").label(LNhccsp2Label);

                String LNhrcsp2Label = "HELICAL RECESS CROSS SECTION PART 2";
                LNhrcxp2.geom().selection().create("csel2", "CumulativeSelection");
                LNhrcxp2.geom().selection("csel2").label(LNhrcsp2Label);

                LNhrcxp2.geom().create("if1", "If");
                LNhrcxp2.geom().feature("if1").set("condition", "Recess>0");
                LNhrcxp2.geom().create("r1", "Rectangle");
                LNhrcxp2.geom().feature("r1").label("Helical Recess Cross Section Part 2");
                LNhrcxp2.geom().feature("r1").set("contributeto", "csel2");
                LNhrcxp2
                    .geom()
                    .feature("r1")
                    .set("pos", new String[] { "Recess/2-Thk_cuff/2", "0" });
                LNhrcxp2.geom().feature("r1").set("base", "center");
                LNhrcxp2.geom().feature("r1").set("size", new String[] { "Recess", "W_elec" });

                LNhrcxp2.geom().create("endif1", "EndIf");

                String LNpcp2cLabel = "Parametric Curve Part 2 (cond)";
                GeomFeature LNpcp2c = model
                    .geom(id)
                    .create(im.next("pc", LNpcp2cLabel), "ParametricCurve");
                LNpcp2c.label(LNpcp2cLabel);
                LNpcp2c.set("contributeto", im.get(im.labels[4]));
                LNpcp2c.set("pos", new int[] { 0, 0, 0 });
                LNpcp2c.set("parmin", "(Rev_insul/2)-(Rev_cond/2)");
                LNpcp2c.set("parmax", "(Rev_insul/2)+(Rev_cond/2)");
                LNpcp2c.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*(R_in+Recess)",
                        "sin(2*pi*s)*(R_in+Recess)",
                        "Center+(L_cuff)*(s/Rev_insul)-(L_cuff/2)",
                    }
                );

                String LNpcp2cfLabel = "Parametric Curve Part 2 (cuff)";
                GeomFeature LNpcp2cf = model
                    .geom(id)
                    .create(im.next("pc", LNpcp2cfLabel), "ParametricCurve");
                LNpcp2cf.label(LNpcp2cfLabel);

                LNpcp2cf.set("contributeto", im.get(im.labels[8]));
                LNpcp2cf.set("pos", new int[] { 0, 0, 0 });
                LNpcp2cf.set("parmin", "(Rev_insul/2)-(Rev_cond/2)");
                LNpcp2cf.set("parmax", "(Rev_insul/2)+(Rev_cond/2)");
                LNpcp2cf.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*(R_in)",
                        "sin(2*pi*s)*(R_in)",
                        "Center+(L_cuff)*(s/Rev_insul)-(L_cuff/2)",
                    }
                );

                model.geom(id).create("if4", "If");
                model.geom(id).feature("if4").set("condition", "Recess==0");

                String mcp2nrLabel = "Make Cuff Part 2 (no recess)";
                GeomFeature mcp2nr = model.geom(id).create(im.next("swe", mcp2nrLabel), "Sweep");
                mcp2nr.label(mcp2nrLabel);
                mcp2nr.set("contributeto", im.get(im.labels[6]));
                mcp2nr.set("crossfaces", true);
                mcp2nr.set("includefinal", false);
                mcp2nr.set("twistcomp", false);
                mcp2nr.set("keep", false);

                mcp2nr.selection("face").named(im.get(LNhicsp2Label) + "_" + im.get(LNhicxp2Label));
                mcp2nr.selection("edge").named(im.get(im.labels[8]));
                mcp2nr.selection("diredge").set(im.get(LNpcp2cfLabel) + "(1)", 1);

                model.geom(id).create("endif4", "EndIf");

                model.geom(id).create("if5", "If");
                model.geom(id).feature("if5").set("condition", "Recess>0");

                String mcp2rLabel = "Make Cuff Part 2 (recess)";
                GeomFeature mcp2r = model.geom(id).create(im.next("swe", mcp2rLabel), "Sweep");
                mcp2r.label(mcp2rLabel);
                mcp2r.set("contributeto", im.get(im.labels[6]));
                mcp2r.set("crossfaces", true);
                mcp2r.set("includefinal", false);
                mcp2r.set("twistcomp", false);
                mcp2r.selection("face").named(im.get(LNhicsp2Label) + "_" + im.get(LNhicxp2Label));
                mcp2r.selection("edge").named(im.get(im.labels[4]));
                mcp2r.selection("diredge").set(im.get(LNpcp2cLabel) + "(1)", 1);

                model.geom(id).create("endif5", "EndIf");

                model.geom(id).create("if2", "If");
                model.geom(id).feature("if2").set("condition", "Recess==0");

                String LNmcp2nrLabel = "Make Conductor Part 2 (no recess)";
                GeomFeature LNmcp2nr = model
                    .geom(id)
                    .create(im.next("swe", LNmcp2nrLabel), "Sweep");
                LNmcp2nr.label(LNmcp2nrLabel);
                LNmcp2nr.set("contributeto", im.get(im.labels[9]));
                LNmcp2nr.set("crossfaces", true);
                LNmcp2nr.set("keep", false);
                LNmcp2nr.set("includefinal", false);
                LNmcp2nr.set("twistcomp", false);
                LNmcp2nr
                    .selection("face")
                    .named(im.get(LNhccxp2Label) + "_" + im.get(LNhccxp2wpresessLabel));
                LNmcp2nr.selection("edge").named(im.get(im.labels[4]));
                LNmcp2nr.selection("diredge").set(im.get(LNpcp2cLabel) + "(1)", 1);

                model.geom(id).create("endif2", "EndIf");

                model.geom(id).create("if3", "If");
                model.geom(id).feature("if3").set("condition", "Recess>0");

                String LNmcprLabel = "Make Conductor Part (recess)";
                GeomFeature LNmcpr = model.geom(id).create(im.next("swe", LNmcprLabel), "Sweep");
                LNmcpr.label(LNmcprLabel);
                LNmcpr.set("contributeto", im.get(im.labels[9]));
                LNmcpr.set("crossfaces", true);
                LNmcpr.set("includefinal", false);
                LNmcpr.set("twistcomp", false);
                LNmcpr.set("keep", false);
                LNmcpr
                    .selection("face")
                    .named(im.get(LNhccxp2Label) + "_" + im.get(LNhccxp2wpresessLabel));
                LNmcpr.selection("edge").named(im.get(im.labels[4]));
                LNmcpr.selection("diredge").set(im.get(LNpcp2cLabel) + "(1)", 1);

                String LNmrp2Label = "Make Recess Part 2";
                GeomFeature LNmrp2 = model.geom(id).create(im.next("swe", LNmrp2Label), "Sweep");
                LNmrp2.label(LNmrp2Label);
                LNmrp2.set("contributeto", im.get(im.labels[3]));
                LNmrp2.set("crossfaces", true);
                LNmrp2.set("keep", false);
                LNmrp2.set("includefinal", false);
                LNmrp2.set("twistcomp", false);
                LNmrp2.selection("face").named(im.get(LNhrcxp2Label) + "_" + "csel2");
                LNmrp2.selection("edge").named(im.get(im.labels[8]));
                LNmrp2.selection("diredge").set(im.get(LNpcp2cLabel) + "(1)", 1);

                model.geom(id).create("endif3", "EndIf");

                String LNsefp2Label = "Select End Face Part 2";
                GeomFeature LNsefp2 = model
                    .geom(id)
                    .create(im.next("ballsel", LNsefp2Label), "BallSelection");
                LNsefp2.set("entitydim", 2);
                LNsefp2.label(LNsefp2Label);
                LNsefp2.set("posx", "cos(2*pi*((Rev_insul/2)+(Rev_cond/2)))*(R_in+Thk_cuff/2)");
                LNsefp2.set("posy", "sin(2*pi*((Rev_insul/2)+(Rev_cond/2)))*(R_in+Thk_cuff/2)");
                LNsefp2.set(
                    "posz",
                    "Center+(L_cuff)*(((Rev_insul/2)+(Rev_cond/2))/Rev_insul)-(L_cuff/2)"
                );
                LNsefp2.set("r", 1);
                LNsefp2.set("contributeto", im.get(im.labels[7]));

                String LNhicsp3Label = "Helical Insulator Cross Section Part 3";
                GeomFeature LNhicsp3 = model
                    .geom(id)
                    .create(im.next("wp", LNhicsp3Label), "WorkPlane");
                LNhicsp3.label(LNhicsp3Label);
                LNhicsp3.set("planetype", "faceparallel");
                LNhicsp3.set("unite", true);
                LNhicsp3.selection("face").named(im.get(im.labels[7]));
                LNhicsp3.geom().selection().create("csel2", "CumulativeSelection");
                LNhicsp3.geom().selection("csel2").label("HELICAL INSULATOR CROSS SECTION PART 3");

                LNhicsp3.geom().create("r1", "Rectangle");
                LNhicsp3.geom().feature("r1").set("contributeto", "csel2");
                LNhicsp3.geom().feature("r1").set("pos", new int[] { 0, 0 });
                LNhicsp3.geom().feature("r1").set("base", "center");
                LNhicsp3.geom().feature("r1").set("size", new String[] { "Thk_cuff", "W_cuff" });

                String LNpcp3Label = "Parametric Curve Part 3";
                GeomFeature LNpcp3 = model
                    .geom(id)
                    .create(im.next("pc", LNpcp3Label), "ParametricCurve");
                LNpcp3.label(LNpcp3Label);
                LNpcp3.set("contributeto", im.get(im.labels[11]));
                LNpcp3.set("pos", new int[] { 0, 0, 0 });
                LNpcp3.set("parmin", "(Rev_insul/2)+(Rev_cond/2)");
                LNpcp3.set("parmax", "Rev_insul");
                LNpcp3.set(
                    "coord",
                    new String[] {
                        "cos(2*pi*s)*(R_in)",
                        "sin(2*pi*s)*(R_in)",
                        "Center+(L_cuff)*(s/Rev_insul)-(L_cuff/2)",
                    }
                );

                String LNmcp3Label = "Make Cuff Part 3";
                GeomFeature LNmcp3 = model.geom(id).create(im.next("swe", LNmcp3Label), "Sweep");
                LNmcp3.label(LNmcp3Label);
                LNmcp3.set("contributeto", im.get(im.labels[10]));
                LNmcp3.set("keep", false);
                LNmcp3.set("includefinal", false);
                LNmcp3.set("twistcomp", false);
                LNmcp3.selection("face").named(im.get(LNhicsp3Label) + "_" + "csel2");
                LNmcp3.selection("edge").named(im.get(im.labels[11]));
                LNmcp3.selection("diredge").set(im.get(LNpcp3Label) + "(1)", 1);

                String LNptsrcLabel = "ptSRC";
                GeomFeature LNptsrc = model.geom(id).create(im.next("pt", LNptsrcLabel), "Point");
                LNptsrc.label(LNptsrcLabel);
                LNptsrc.set("contributeto", im.get(im.labels[5]));
                LNptsrc.set(
                    "p",
                    new String[] {
                        "cos(2*pi*Rev_insul*(1.25/2.5))*(Recess+(Thk_elec/2)+R_in)",
                        "sin(2*pi*Rev_insul*(1.25/2.5))*(Recess+(Thk_elec/2)+R_in)",
                        "Center",
                    }
                );

                String LNuspLabel = "Union Silicone Parts";
                model.geom(id).create(im.next("uni", LNuspLabel), "Union");

                model.geom(id).create("if6", "If");
                model.geom(id).feature("if6").set("condition", "Recess>0");
                model
                    .geom(id)
                    .feature(im.get(LNuspLabel))
                    .selection("input")
                    .set(im.get(LNmcp1Label), im.get(mcp2rLabel), im.get(LNmcp3Label));
                model.geom(id).create("endif6", "EndIf");

                model.geom(id).create("if7", "If");
                model.geom(id).feature("if7").set("condition", "Recess==0");
                model
                    .geom(id)
                    .feature(im.get(LNuspLabel))
                    .selection("input")
                    .set(im.get(LNmcp1Label), im.get(mcp2nrLabel), im.get(LNmcp3Label));
                model.geom(id).create("endif7", "EndIf");

                model.geom(id).selection(im.get("CUFF FINAL")).label("CUFF FINAL");
                model
                    .geom(id)
                    .feature(im.get(LNuspLabel))
                    .set("contributeto", im.get("CUFF FINAL"));

                model.geom(id).run();

                break;
            default:
                throw new IllegalArgumentException(
                    "No implementation for part primitive name: " + pseudonym
                );
        }

        // if im was not edited for some reason, return null
        if (im.count() == 0) return null;
        return im;
    }

    /**
     * Create instance of an ALREADY CREATED part primitive
     * @param instanceID the part instance COMSOL id (unique) --> use mw.im.next in call (pi)
     * @param instanceLabel the name for this instance --> unique, and NOT the same as pseudonym
     * @param pseudonym which primitive to create (it must have already been created in createCuffPartPrimitive())
     * @param mw the ModelWrapper to act upon
     * @param instanceParams instance parameters as loaded in from the associated JSON configuration (in ModelWrapper)
     * @param name the name of the cuff preset
     * @param index index of the cuff within the model's cuff list. Added for multiple cuff functionality
     * @param cuffIndex cuff index defined in model.json
     * @throws IllegalArgumentException if the primitive specified by pseudonym has not been created
     */
    public static void createCuffPartInstance(
        String instanceID,
        String instanceLabel,
        String pseudonym,
        ModelWrapper mw,
        JSONObject instanceParams,
        String name,
        int index,
        int cuffIndex
    ) throws IllegalArgumentException {
        Model model = mw.getModel();

        GeomFeature partInstance = model
            .component("comp1")
            .geom("geom1")
            .create(instanceID, "PartInstance");
        partInstance.label(instanceLabel);
        partInstance.set("part", mw.im.get(pseudonym));

        partInstance.set(
            "displ",
            new String[] {
                name + "_" + index + "_cuff_shift_x",
                name + "_" + index + "_cuff_shift_y",
                name + "_" + index + "_cuff_shift_z",
            }
        ); // moves cuff around the nerve
        partInstance.set("rot", name + "_" + index + "_cuff_rot");

        JSONObject itemObject = instanceParams.getJSONObject("def");
        IdentifierManager myIM = mw.getPartPrimitiveIM(pseudonym);
        if (myIM == null) throw new IllegalArgumentException(
            "IdentfierManager not created for name: " + pseudonym
        );

        String[] myLabels = myIM.labels; // may be null, but that is ok if not used

        // set instantiation parameters and import selections
        switch (pseudonym) {
            case "TubeCuff_Primitive":
                // set instantiation parameters
                String[] tubeCuffParameters = {
                    "N_holes",
                    "Tube_theta",
                    "Center",
                    "R_in",
                    "R_out",
                    "Tube_L",
                    "Rot_def",
                    "D_hole",
                    "Buffer_hole",
                    "L_holecenter_cuffseam",
                    "Pitch_holecenter_holecenter",
                };

                for (String param : tubeCuffParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "on"
                ); // CUFF FINAL

                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // OUTER CUFF SURFACE
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "off"
                ); // CUFF wGAP PRE HOLES
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "off"
                ); // CUFF PRE GAP
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".dom",
                    "off"
                ); // CUFF PRE GAP PRE HOLES
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".dom",
                    "off"
                ); // CUFF GAP CROSS SECTION
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[7]) + ".dom",
                    "off"
                ); // CUFF GAP
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".dom",
                    "off"
                ); // CUFF PRE HOLES
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".dom",
                    "off"
                ); // HOLE 1
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".dom",
                    "off"
                ); // HOLE 2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[11]) + ".dom",
                    "off"
                ); // HOLES

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // OUTER CUFF SURFACE
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".pnt",
                    "off"
                ); // CUFF FINAL
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".pnt",
                    "off"
                ); // CUFF wGAP PRE HOLES
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".pnt",
                    "off"
                ); // CUFF PRE GAP
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".pnt",
                    "off"
                ); // CUFF PRE GAP PRE HOLES
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".pnt",
                    "off"
                ); // CUFF GAP CROSS SECTION
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[7]) + ".pnt",
                    "off"
                ); // CUFF GAP
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".pnt",
                    "off"
                ); // CUFF PRE HOLES
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".pnt",
                    "off"
                ); // HOLE 1
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".pnt",
                    "off"
                ); // HOLE 2
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[11]) + ".pnt",
                    "off"
                ); // HOLES

                break;
            case "RibbonContact_Primitive":
                // set instantiation parameters
                String[] ribbonContactParameters = {
                    "Ribbon_thk",
                    "Ribbon_z",
                    "R_in",
                    "Ribbon_recess",
                    "Center",
                    "Ribbon_theta",
                    "Rot_def",
                };

                for (String param : ribbonContactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // RECESS CROSS SECTION
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "on"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "on"
                ); // RECESS FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // RECESS CROSS SECTION
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".pnt",
                    "on"
                ); // SRC
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".pnt",
                    "off"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".pnt",
                    "off"
                ); // RECESS FINAL

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[2])
                );

                break;
            case "TubeCuffSweep_Primitive":
                // set instantiation parameters
                String[] TubeCuffSweepParameters = {
                    "Cuff_thk",
                    "Cuff_z",
                    "R_in",
                    "Center",
                    "Cuff_theta",
                    "Rot_def",
                };

                for (String param : TubeCuffSweepParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "on"
                ); // CUFF FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // CUFF FINAL

                // assign physics

                break;
            case "WireContact_Primitive":
                // set instantiation parameters
                String[] wireContactParameters = {
                    "Wire_r",
                    "R_in",
                    "Center",
                    "Pitch",
                    "Wire_sep",
                    "Wire_theta",
                };

                for (String param : wireContactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "on"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "off"
                ); // SRC

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".pnt",
                    "on"
                ); // SRC

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[2])
                );

                break;
            case "CircleContact_Primitive":
                // set instantiation parameters
                String[] circleContactParameters = {
                    "Circle_recess",
                    "Rotation_angle",
                    "Center",
                    "Circle_def",
                    "R_in",
                    "Circle_thk",
                    "Overshoot",
                    "Circle_diam",
                    "L",
                };

                for (String param : circleContactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // PRE CUT CONTACT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "on"
                ); // RECESS FINAL
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "off"
                ); // RECESS OVERSHOOT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".dom",
                    "off"
                ); // PLANE FOR CONTACT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".dom",
                    "on"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[7]) + ".dom",
                    "off"
                ); // CONTACT CUTTER OUT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".dom",
                    "off"
                ); // BASE CONTACT PLANE (PRE ROTATION)
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".dom",
                    "off"
                ); // PLANE FOR RECESS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".dom",
                    "off"
                ); // PRE CUT RECESS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[11]) + ".dom",
                    "off"
                ); // RECESS CUTTER IN
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[12]) + ".dom",
                    "off"
                ); // RECESS CUTTER OUT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[13]) + ".dom",
                    "off"
                ); // BASE PLANE (PRE ROTATION)

                partInstance.setEntry(
                    "selkeepobj",
                    instanceID + "_" + myIM.get(myLabels[4]),
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepobj",
                    instanceID + "_" + myIM.get(myLabels[6]),
                    "off"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeepobj",
                    instanceID + "_" + myIM.get(myLabels[7]),
                    "off"
                ); // CONTACT CUTTER OUT

                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".bnd",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".bnd",
                    "off"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".bnd",
                    "off"
                ); // CONTACT CUTTER OUT
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[12]) + ".bnd",
                    "off"
                ); // RECESS CUTTER OUT
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[13]) + ".bnd",
                    "off"
                ); // BASE PLANE (PRE ROTATION)

                partInstance.setEntry(
                    "selkeepedg",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".edg",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepedg",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".edg",
                    "off"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeepedg",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".edg",
                    "off"
                ); // CONTACT CUTTER OUT

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // PRE CUT CONTACT
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".pnt",
                    "off"
                ); // RECESS FINAL
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".pnt",
                    "on"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".pnt",
                    "off"
                ); // PLANE FOR CONTACT
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".pnt",
                    "off"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[7]) + ".pnt",
                    "off"
                ); // CONTACT CUTTER OUT
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".pnt",
                    "off"
                ); // CONTACT CUTTER OUT
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".pnt",
                    "off"
                ); // PLANE FOR RECESS
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".pnt",
                    "off"
                ); // PRE CUT RECESS
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[11]) + ".pnt",
                    "off"
                ); // RECESS CUTTER IN
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[12]) + ".pnt",
                    "off"
                ); // RECESS CUTTER OUT
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[13]) + ".pnt",
                    "off"
                ); // BASE PLANE (PRE ROTATION)

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[4])
                );

                break;
            case "HelicalContact_Primitive":
                // set instantiation parameters
                String[] pc_helicalCuffnContactParameters = { "Center", "Corr" };

                for (String param : pc_helicalCuffnContactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                partInstance.set("rot", name + "_" + index + "_cuff_rot + corr_LN");

                model
                    .component("comp1")
                    .geom("geom1")
                    .feature(instanceID)
                    .setEntry("inputexpr", "Center", (String) itemObject.get("Center"));

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "off"
                ); // PC2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".dom",
                    "on"
                ); // Conductorp2

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".pnt",
                    "off"
                ); // PC2
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".pnt",
                    "on"
                ); // SRC
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".pnt",
                    "off"
                ); // Conductorp2

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[4])
                );

                break;
            case "HelicalCuffnContact_Primitive":
                // set instantiation parameters
                String[] helicalCuffnContactParameters = {
                    "Center",
                    "Corr",
                    "rev_BD_insul",
                    "rev_BD_cond",
                };

                for (String param : helicalCuffnContactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                partInstance.set("rot", name + "_" + index + "_cuff_rot + corr_LN");

                model
                    .component("comp1")
                    .geom("geom1")
                    .feature(instanceID)
                    .setEntry("inputexpr", "Center", (String) itemObject.get("Center"));

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // Cuffp1
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "off"
                ); // PC2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".dom",
                    "off"
                ); // Cuffp2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".dom",
                    "on"
                ); // Conductorp2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".dom",
                    "off"
                ); // Cuffp3
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".dom",
                    "off"
                ); // PC3
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".dom",
                    "on"
                ); // CUFF FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // Cuffp1
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".pnt",
                    "off"
                ); // PC2
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".pnt",
                    "on"
                ); // SRC
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".pnt",
                    "off"
                ); // Cuffp2
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".pnt",
                    "off"
                ); // Conductorp2
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".pnt",
                    "off"
                ); // Cuffp3
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".pnt",
                    "off"
                ); // PC3
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".pnt",
                    "off"
                ); // CUFF FINAL

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[4])
                );

                break;
            case "RectangleContact_Primitive":
                // set instantiation parameters
                String[] rectangleContactParameters = {
                    "Center",
                    "Rotation_angle",
                    "Rect_w",
                    "Rect_z",
                    "Rect_fillet",
                    "L_cuff",
                    "R_in",
                    "Rect_recess",
                    "Rect_thk",
                    "Rect_def",
                };

                for (String param : rectangleContactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);

                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // SEL INNER EXCESS CONTACT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "off"
                ); // INNER CONTACT CUTTER
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "off"
                ); // SEL OUTER EXCESS RECESS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "off"
                ); // SEL INNER EXCESS RECESS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".dom",
                    "off"
                ); // OUTER CUTTER
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".dom",
                    "on"
                ); // FINAL RECESS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[7]) + ".dom",
                    "off"
                ); // RECESS CROSS SECTION
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".dom",
                    "off"
                ); // OUTER RECESS CUTTER
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".dom",
                    "off"
                ); // RECESS PRE CUTS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".dom",
                    "off"
                ); // INNER RECESS CUTTER
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[11]) + ".dom",
                    "on"
                ); // FINAL CONTACT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[12]) + ".dom",
                    "off"
                ); // SEL OUTER EXCESS CONTACT
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[13]) + ".dom",
                    "off"
                ); // SEL OUTER EXCESS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[14]) + ".dom",
                    "off"
                ); // SEL INNER EXCESS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[15]) + ".dom",
                    "off"
                ); // BASE CONTACT PLANE (PRE ROTATION)
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[16]) + ".dom",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[17]) + ".dom",
                    "off"
                ); // CONTACT PRE CUTS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[18]) + ".dom",
                    "off"
                ); // CONTACT CROSS SECTION
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[19]) + ".dom",
                    "off"
                ); // INNER CUFF CUTTER
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[20]) + ".dom",
                    "off"
                ); // OUTER CUFF CUTTER
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[21]) + ".dom",
                    "off"
                ); // FINAL
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[22]) + ".dom",
                    "off"
                ); // INNER CUTTER

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".pnt",
                    "off"
                ); // INNER CONTACT CUTTER
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".pnt",
                    "off"
                ); // OUTER CUTTER
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".pnt",
                    "off"
                ); // FINAL RECESS
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[7]) + ".pnt",
                    "off"
                ); // RECESS CROSS SECTION
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".pnt",
                    "off"
                ); // OUTER RECESS CUTTER
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".pnt",
                    "off"
                ); // RECESS PRE CUTS
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".pnt",
                    "off"
                ); // INNER RECESS CUTTER
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[11]) + ".pnt",
                    "off"
                ); // FINAL CONTACT
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[16]) + ".pnt",
                    "on"
                ); // SRC
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[13]) + ".pnt",
                    "off"
                ); // SEL OUTER EXCESS
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[14]) + ".pnt",
                    "off"
                ); // SEL INNER EXCESS
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[15]) + ".pnt",
                    "off"
                ); // BASE CONTACT PLANE (PRE ROTATION)
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[17]) + ".pnt",
                    "off"
                ); // CONTACT PRE CUTS
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[18]) + ".pnt",
                    "off"
                ); // CONTACT CROSS SECTION
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[19]) + ".pnt",
                    "off"
                ); // INNER CUFF CUTTER
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[20]) + ".pnt",
                    "off"
                ); // OUTER CUFF CUTTER
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[21]) + ".pnt",
                    "off"
                ); // FINAL
                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[22]) + ".pnt",
                    "off"
                ); // INNER CUTTER

                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[13]) + ".bnd",
                    "off"
                ); // SEL OUTER EXCESS
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[14]) + ".bnd",
                    "off"
                ); // SEL INNER EXCESS
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[15]) + ".bnd",
                    "off"
                ); // BASE CONTACT PLANE (PRE ROTATION)
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[16]) + ".bnd",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[17]) + ".bnd",
                    "off"
                ); // CONTACT PRE CUTS
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[18]) + ".bnd",
                    "off"
                ); // CONTACT CROSS SECTION
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[19]) + ".bnd",
                    "off"
                ); // INNER CUFF CUTTER
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[20]) + ".bnd",
                    "off"
                ); // OUTER CUFF CUTTER
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[21]) + ".bnd",
                    "off"
                ); // FINAL
                partInstance.setEntry(
                    "selkeepbnd",
                    instanceID + "_" + myIM.get(myLabels[22]) + ".bnd",
                    "off"
                ); // INNER CUTTER

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[16])
                );

                break;
            case "uContact_Primitive":
                // set instantiation parameters
                String[] uContactParameters = {
                    "Center",
                    "R_in",
                    "U_tangent",
                    "U_thk",
                    "U_z",
                    "U_recess",
                };

                for (String param : uContactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                    "off"
                ); // CONTACT XS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "on"
                ); // CONTACT FINAL
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "off"
                ); // SRC
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "on"
                ); // RECESS FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // CONTACT FINAL

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[2])
                );

                break;
            case "uCuff_Primitive":
                // set instantiation parameters
                String[] uCuffParameters = {
                    "Center",
                    "R_in",
                    "U_tangent",
                    "R_out",
                    "U_L",
                    "U_shift_x",
                    "U_shift_y",
                    "U_gap",
                };

                for (String param : uCuffParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                    "off"
                ); // CUFF XS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "on"
                ); // CUFF FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // CUFF FINAL

                break;
            case "uCuffFill_Primitive":
                // set instantiation parameters
                String[] uCuffFillParameters = { "Center", "R_in", "U_tangent", "L" };

                for (String param : uCuffFillParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                    "on"
                ); // FILL FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".pnt",
                    "off"
                ); // FILL FINAL

                break;
            case "CuffFill_Primitive":
                // set instantiation parameters
                String[] cuffFillParameters = {
                    "Radius",
                    "Thk",
                    "L",
                    "Center",
                    "x_shift",
                    "y_shift",
                };

                for (String param : cuffFillParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                    "on"
                ); // CUFF FILL FINAL

                break;
            case "uCuffTrap_Primitive":
                // set instantiation parameters
                String[] uCuffTrapParameters = {
                    "R_in",
                    "Ut_tangent",
                    "Rt_out",
                    "Ut_shift_x",
                    "Ut_shift_y",
                    "Ut_gap",
                    "Center",
                    "Ut_L",
                    "Ut_trap_base",
                };

                for (String param : uCuffTrapParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                    "off"
                ); // CUFF XS
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "on"
                ); // CUFF FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".pnt",
                    "off"
                ); // CUFF FINAL

                break;
            case "uContactTrap_Primitive":
                // set instantiation parameters
                String[] uContactTrapParameters = {
                    "Center",
                    "R_in",
                    "Ut_thk",
                    "Ut_tangent",
                    "Ut_recess",
                    "Ut_z",
                    "Ut_trap_base",
                };

                for (String param : uContactTrapParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                    "off"
                ); // RECESS XS Trap
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // CONTACT XS Trap
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "on"
                ); // RECESS FINAL TRAP
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "on"
                ); // CONTACT FINAL TRAP

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".pnt",
                    "on"
                ); // SRC FINAL

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[4])
                );

                break;
            case "ArleContact_Primitive":
                // set instantiation parameters
                String[] ArleCopntactParameters = {
                    "Gauge_AC",
                    "Wrap_AC",
                    "R_in",
                    "L_AC",
                    "Center",
                };

                for (String param : ArleCopntactParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                // imports
                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[0]) + ".dom",
                    "off"
                ); // "SEMI CIRC CONTACT"
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // "Semi Sweep"
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "on"
                ); // "ARLE CONTACT FINAL"

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".pnt",
                    "on"
                ); // SRC FINAL

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[3])
                );

                break;
            case "LivaNova_Primitive":
                // set instantiation parameters
                String[] LivaNovaParameters = {
                    "Center",
                    "Thk_cuff",
                    "W_cuff",
                    "R_in",
                    "L_cuff",
                    "Rev_insul",
                    "Rev_cond",
                    "Recess",
                    "Thk_elec",
                    "W_elec",
                };

                for (String param : LivaNovaParameters) {
                    partInstance.setEntry("inputexpr", param, (String) itemObject.get(param));
                }

                partInstance.set("rot", name + "_" + index + "_cuff_rot + corr_LN");

                // imports

                partInstance.set("selkeepnoncontr", false);
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[2]) + ".dom",
                    "off"
                ); // CUFF P1
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[3]) + ".dom",
                    "on"
                ); // RECESS P2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[6]) + ".dom",
                    "off"
                ); // CUFF P2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[9]) + ".dom",
                    "on"
                ); // CONDUCTOR P2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[10]) + ".dom",
                    "off"
                ); // CUFF P3
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[12]) + ".dom",
                    "on"
                ); // CUFF FINAL

                partInstance.setEntry(
                    "selkeeppnt",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".pnt",
                    "on"
                ); // SRC FINAL

                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[1]) + ".dom",
                    "off"
                ); // PC1
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[4]) + ".dom",
                    "off"
                ); // PC2
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[5]) + ".dom",
                    "off"
                ); // SRC FINAL
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[8]) + ".dom",
                    "off"
                ); // PC3
                partInstance.setEntry(
                    "selkeepdom",
                    instanceID + "_" + myIM.get(myLabels[11]) + ".dom",
                    "off"
                ); // PC4

                Part.addPointCurrentSource(
                    mw,
                    model,
                    cuffIndex,
                    instanceLabel,
                    myIM.get(myLabels[5])
                );

                break;
            default:
                throw new IllegalArgumentException(
                    "No implementation for part instance name: " + pseudonym
                );
        }
    }

    /**
     * Build a part of the nerve.
     * @param pseudonym the type of part to build (i.e. FascicleCI, FascicleMesh, Epineurium)
     * @param index what to call this part (i.e. fascicle# or epi#)
     * @param path the absolute (general) directory which holds the trace data in SECTIONWISE2D format
     * @param mw the ModelWrapper which is acted upon
     * @param tracePaths the relative (specific) paths to the required trace data (two keys: "inners" and "outer")
     * @throws IllegalArgumentException there is not a nerve part to build of that type (for typos probably)
     */
    public static void createNervePartInstance(
        String pseudonym,
        int index,
        String path,
        ModelWrapper mw,
        HashMap<String, String[]> tracePaths,
        JSONObject sampleData,
        ModelParamGroup nerveParams,
        JSONObject modelData
    ) throws Exception {
        Model model = mw.getModel();
        IdentifierManager im = mw.im;

        switch (pseudonym) {
            case "FascicleCI":
                String ci_outer_name = "outer" + index;
                String ci_inner_name = "inner" + index;

                String ci_inner_path = path + "/inners/" + tracePaths.get("inners")[0];
                String ci_outer_path = path + "/outer/" + tracePaths.get("outer")[0];

                String ci_inner = tracePaths.get("inners")[0];
                String ci_inner_index = ci_inner.split("\\.")[0];

                String fascicleCI_Inner_Label = ci_inner_name + "_INNERS_CI";
                String fascicleCI_Endo_Label = ci_inner_name + "_ENDONEURIUM";

                im.labels =
                    new String[] {
                        fascicleCI_Inner_Label, //0
                        fascicleCI_Endo_Label,
                    };

                for (String cselFascicleCILabel : im.labels) {
                    model
                        .component("comp1")
                        .geom("geom1")
                        .selection()
                        .create(im.next("csel", cselFascicleCILabel), "CumulativeSelection")
                        .label(cselFascicleCILabel);
                }

                JSONObject fascicle = sampleData
                    .getJSONObject("Morphology")
                    .getJSONArray("Fascicles")
                    .getJSONObject(index);
                String morphology_unit = "micrometer";

                String fascicleCICXLabel = ci_inner_name + " Inner Geometry";
                GeomFeature fascicleCICX = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("wp", fascicleCICXLabel), "WorkPlane");
                fascicleCICX.label(fascicleCICXLabel);
                fascicleCICX.set("contributeto", im.get(fascicleCI_Inner_Label));
                fascicleCICX.set("unite", true);

                String icLabel = ci_inner_name + "_IC";
                fascicleCICX
                    .geom()
                    .selection()
                    .create(im.next("csel", icLabel), "CumulativeSelection");
                fascicleCICX.geom().selection(im.get(icLabel)).label(icLabel);

                String icnameLabel = ci_inner_name + " Inner Trace " + ci_inner_index;
                GeomFeature ic = model
                    .component("comp1")
                    .geom("geom1")
                    .feature(im.get(fascicleCICXLabel))
                    .geom()
                    .create(im.next("ic", icnameLabel), "InterpolationCurve");
                ic.label(icnameLabel);
                ic.set("contributeto", im.get(icLabel));
                ic.set("source", "file");
                ic.set("filename", ci_inner_path);
                ic.set("type", "closed");

                if (modelData.has("inner_interp_tol") && !modelData.has("trace_interp_tol")) {
                    ic.set("rtol", modelData.getDouble("inner_interp_tol"));
                } else if (
                    modelData.has("trace_interp_tol") && !modelData.has("inner_interp_tol")
                ) {
                    ic.set("rtol", modelData.getDouble("trace_interp_tol"));
                } else if (modelData.has("trace_interp_tol") && modelData.has("inner_interp_tol")) {
                    throw new Exception(
                        "Both trace_interp_tol and inner_interp_tol defined in Model. " +
                        "Use new convention for inners (inner_interp_tol) and outers (outer_interp_tol) separately!"
                    );
                }

                String conv2solidLabel = ci_inner_name + " Inner Surface " + ci_inner_index;
                GeomFeature conv2solid = model
                    .component("comp1")
                    .geom("geom1")
                    .feature(im.get(fascicleCICXLabel))
                    .geom()
                    .create(im.next("csol", conv2solidLabel), "ConvertToSolid");
                conv2solid.label(conv2solidLabel);
                conv2solid.selection("input").named(im.get(icLabel));

                String makefascicleLabel = ci_inner_name + " Make Endoneurium";
                GeomFeature makefascicle = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("ext", makefascicleLabel), "Extrude");
                makefascicle.label(makefascicleLabel);
                makefascicle.set("contributeto", im.get(fascicleCI_Endo_Label));
                makefascicle.setIndex("distance", "z_nerve", 0);
                makefascicle.selection("input").named(im.get(fascicleCI_Inner_Label));

                // Add fascicle domains to ALL_NERVE_PARTS_UNION and ENDO_UNION for later assigning to materials and mesh
                String[] fascicleCIEndoUnions = {
                    ModelWrapper.ALL_NERVE_PARTS_UNION,
                    ModelWrapper.ENDO_UNION,
                };
                mw.contributeToUnions(im.get(makefascicleLabel), fascicleCIEndoUnions);

                // Add physics
                String ciLabel = ci_inner_name + " ContactImpedance";
                PhysicsFeature ci = model
                    .component("comp1")
                    .physics("ec")
                    .create(im.next("ci", ciLabel), "ContactImpedance", 2);
                ci.label(ciLabel);
                ci.selection().named("geom1_" + im.get(fascicleCI_Endo_Label) + "_bnd");
                ci.set("spec_type", "surfimp");
                // if inners only
                String mask_input_mode = sampleData.getJSONObject("modes").getString("mask_input");
                String ci_perineurium_thickness_mode = sampleData
                    .getJSONObject("modes")
                    .getString("ci_perineurium_thickness");

                String separate = "INNER_AND_OUTER_SEPARATE";
                String compiled = "INNER_AND_OUTER_COMPILED";
                String inners = "INNERS";
                String outers = "OUTERS";
                String measured = "MEASURED";

                final boolean both_inners_and_outers =
                    (mask_input_mode.compareTo(separate)) == 0 ||
                    (mask_input_mode.compareTo(compiled)) == 0;
                if (
                    both_inners_and_outers &&
                    (ci_perineurium_thickness_mode.compareTo(measured) == 0)
                ) {
                    String name_area_inner = ci_inner_name + "_area";
                    String name_area_outer = ci_outer_name + "_area";

                    Double inner_area =
                        ((JSONObject) fascicle.getJSONArray("inners").get(0)).getDouble("area");
                    Double outer_area = ((JSONObject) fascicle.get("outer")).getDouble("area");

                    nerveParams.set(
                        name_area_inner,
                        inner_area + " [" + morphology_unit + "^2]",
                        ci_inner_path
                    );
                    nerveParams.set(
                        name_area_outer,
                        outer_area + " [" + morphology_unit + "^2]",
                        ci_outer_path
                    );

                    String rhos =
                        "(1/sigma_perineurium)*(sqrt(" +
                        name_area_outer +
                        "/pi) - sqrt(" +
                        name_area_inner +
                        "/pi))"; // A = pi*r^2; r = sqrt(A/pi); thk = sqrt(A_out/pi)-sqrt(A_in/pi); Rm = rho*thk
                    ci.set("rhos", rhos);
                } else if (
                    (
                        both_inners_and_outers &&
                        !(ci_perineurium_thickness_mode.compareTo(measured) == 0)
                    ) ||
                    (mask_input_mode.compareTo(inners) == 0)
                ) {
                    String name_area_inner = ci_inner_name + "_area";

                    Double inner_area =
                        ((JSONObject) fascicle.getJSONArray("inners").get(0)).getDouble("area");

                    nerveParams.set(
                        name_area_inner,
                        inner_area + " [" + morphology_unit + "^2]",
                        ci_inner_path
                    );

                    String rhos =
                        "(1/sigma_perineurium)*(ci_a*2*sqrt(" + name_area_inner + "/pi)+ci_b)"; // A = pi*r^2; r = sqrt(A/pi); d = 2*sqrt(A/pi); thk = 0.03*2*sqrt(A/pi); Rm = rho*thk
                    ci.set("rhos", rhos);
                } else if (mask_input_mode.compareTo(outers) == 0) {
                    System.out.println(
                        "OUTERS ONLY NOT IMPLEMENTED - NO PERI CONTACT IMPEDANCE SET"
                    );
                }
                model.nodeGroup(im.get("Contact Impedances")).add(im.get(ciLabel));

                break;
            case "FascicleMesh":
                String mesh_name = "outer" + index;

                String fascicleMesh_Inners_Label = mesh_name + "_INNERS";
                String fascicleMesh_Outer_Label = mesh_name + "_OUTER";
                String fascicleMesh_Peri_Label = mesh_name + "_PERINEURIUM";
                String fascicleMesh_Endo_Label = mesh_name + "_ENDONEURIUM";

                im.labels =
                    new String[] {
                        fascicleMesh_Inners_Label, //0
                        fascicleMesh_Outer_Label,
                        fascicleMesh_Peri_Label,
                        fascicleMesh_Endo_Label,
                    };

                for (String cselFascicleMeshLabel : im.labels) {
                    model
                        .component("comp1")
                        .geom("geom1")
                        .selection()
                        .create(im.next("csel", cselFascicleMeshLabel), "CumulativeSelection")
                        .label(cselFascicleMeshLabel);
                }

                String innersPlaneLabel = "outer" + index + " Inners Geometry";
                GeomFeature innersPlane = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("wp", innersPlaneLabel), "WorkPlane");
                innersPlane.set("contributeto", im.get(fascicleMesh_Inners_Label));
                innersPlane.set("selresult", true);
                innersPlane.set("unite", true);
                innersPlane.label(innersPlaneLabel);

                String innersselLabel = "outer" + index + " inners_all";
                innersPlane
                    .geom()
                    .selection()
                    .create(im.next("csel", innersselLabel), "CumulativeSelection");
                innersPlane.geom().selection(im.get(innersselLabel)).label(innersselLabel);

                // loop over inners (make IC, convert to solid, add to inners_all)
                for (String inner : tracePaths.get("inners")) {
                    String mesh_inner_path = path + "/inners/" + inner;
                    String mesh_inner_index = inner.split("\\.")[0];

                    String icselLabel = "outer" + index + " IC" + mesh_inner_index;
                    innersPlane
                        .geom()
                        .selection()
                        .create(im.next("csel", icselLabel), "CumulativeSelection");
                    innersPlane.geom().selection(im.get(icselLabel)).label(icselLabel);

                    String icTraceLabel = "outer" + index + " Inner Trace " + mesh_inner_index;
                    GeomFeature icMesh = innersPlane
                        .geom()
                        .create(im.next("ic", icTraceLabel), "InterpolationCurve");
                    icMesh.label(icTraceLabel);
                    icMesh.set("contributeto", im.get(icselLabel));
                    icMesh.set("source", "file");
                    icMesh.set("filename", mesh_inner_path);
                    icMesh.set("type", "closed");

                    if (modelData.has("inner_interp_tol") && !modelData.has("trace_interp_tol")) {
                        icMesh.set("rtol", modelData.getDouble("inner_interp_tol"));
                    } else if (
                        modelData.has("trace_interp_tol") && !modelData.has("inner_interp_tol")
                    ) {
                        icMesh.set("rtol", modelData.getDouble("trace_interp_tol")); // backwards compatibility
                    } else if (
                        modelData.has("trace_interp_tol") && modelData.has("inner_interp_tol")
                    ) {
                        throw new Exception(
                            "Both trace_interp_tol and inner_interp_tol defined in Model. " +
                            "Use new convention for inners (inner_interp_tol) and outers (outer_interp_tol) separately!"
                        );
                    }

                    String icSurfLabel = "outer" + index + " Inner Surface " + mesh_inner_index;
                    GeomFeature icSurf = innersPlane
                        .geom()
                        .create(im.next("csol", icSurfLabel), "ConvertToSolid");
                    icSurf.label(icSurfLabel);
                    icSurf.set("contributeto", im.get(innersselLabel));
                    icSurf.set("keep", false);
                    icSurf.selection("input").named(im.get(icselLabel));
                }

                String outerPlaneLabel = "outer" + index + " Outer Geometry";
                GeomFeature outerPlane = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("wp", outerPlaneLabel), "WorkPlane");
                outerPlane.label(outerPlaneLabel);
                outerPlane.set("contributeto", im.get(fascicleMesh_Outer_Label));
                outerPlane.set("unite", true);

                String oc1Label = "outer" + index + " OC";
                outerPlane
                    .geom()
                    .selection()
                    .create(im.next("csel", oc1Label), "CumulativeSelection");
                outerPlane.geom().selection(im.get(oc1Label)).label(oc1Label);

                String outerselLabel = "outer" + index + " sel";
                outerPlane
                    .geom()
                    .selection()
                    .create(im.next("csel", outerselLabel), "CumulativeSelection");
                outerPlane.geom().selection(im.get(outerselLabel)).label(outerselLabel);

                String mesh_outer_path = path + "/outer/" + tracePaths.get("outer")[0];
                String outeric1Label = "outer" + index + " Outer Trace";
                GeomFeature outeric1 = outerPlane
                    .geom()
                    .create(im.next("ic", outeric1Label), "InterpolationCurve");
                outeric1.label(outeric1Label);
                outeric1.set("contributeto", im.get(oc1Label));
                outeric1.set("source", "file");
                outeric1.set("filename", mesh_outer_path);
                outeric1.set("type", "closed");

                if (modelData.has("outer_interp_tol") && !modelData.has("trace_interp_tol")) {
                    outeric1.set("rtol", modelData.getDouble("outer_interp_tol"));
                } else if (
                    modelData.has("trace_interp_tol") && !modelData.has("outer_interp_tol")
                ) {
                    outeric1.set("rtol", modelData.getDouble("trace_interp_tol")); // backwards compatibility
                } else if (modelData.has("trace_interp_tol") && modelData.has("outer_interp_tol")) {
                    throw new Exception(
                        "Both trace_interp_tol and inner_interp_tol defined in Model. " +
                        "Use new convention for inners (outer_interp_tol), outers (outer_interp_tol), and nerve (nerve_interp_tol) separately!"
                    );
                }

                String outericSurfaceLabel = "outer" + index + " Outer Surface";
                outerPlane.geom().create(im.next("csol", outericSurfaceLabel), "ConvertToSolid");
                outerPlane.geom().feature(im.get(outericSurfaceLabel)).set("keep", false);
                outerPlane
                    .geom()
                    .feature(im.get(outericSurfaceLabel))
                    .selection("input")
                    .named(im.get(oc1Label));
                outerPlane
                    .geom()
                    .feature(im.get(outericSurfaceLabel))
                    .set("contributeto", im.get(outerselLabel));
                outerPlane.geom().feature(im.get(outericSurfaceLabel)).label(outericSurfaceLabel);

                String makePeriLabel = "outer" + index + " Make Perineurium";
                GeomFeature makePeri = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("ext", makePeriLabel), "Extrude");
                makePeri.label(makePeriLabel);
                makePeri.set("contributeto", im.get(fascicleMesh_Peri_Label));
                makePeri.set("workplane", im.get(outerPlaneLabel));
                makePeri.setIndex("distance", "z_nerve", 0);
                makePeri.selection("input").named(im.get(fascicleMesh_Outer_Label));

                // Add fascicle domains to ALL_NERVE_PARTS_UNION and ENDO_UNION for later assigning to materials
                String[] fascicleMeshPeriUnions = {
                    ModelWrapper.ALL_NERVE_PARTS_UNION,
                    ModelWrapper.PERI_UNION,
                };
                mw.contributeToUnions(im.get(makePeriLabel), fascicleMeshPeriUnions);

                String makeEndoLabel = "outer" + index + " Make Endoneurium";
                GeomFeature makeEndo = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("ext", makeEndoLabel), "Extrude");
                makeEndo.label(makeEndoLabel);
                makeEndo.set("contributeto", im.get(fascicleMesh_Endo_Label));
                makeEndo.set("workplane", im.get(innersPlaneLabel));
                makeEndo.setIndex("distance", "z_nerve", 0);
                makeEndo.selection("input").named(im.get(fascicleMesh_Inners_Label));

                // Add fascicle domains to ALL_NERVE_PARTS_UNION and ENDO_UNION for later assigning to materials
                String[] fascicleMeshEndoUnions = {
                    ModelWrapper.ALL_NERVE_PARTS_UNION,
                    ModelWrapper.ENDO_UNION,
                };
                mw.contributeToUnions(im.get(makeEndoLabel), fascicleMeshEndoUnions);

                break;
            case "Epi_circle":
                im.labels =
                    new String[] {
                        "EPINEURIUM", //0
                        "EPIXS",
                    };

                for (String cselEpineuriumLabel : im.labels) {
                    model
                        .component("comp1")
                        .geom("geom1")
                        .selection()
                        .create(im.next("csel", cselEpineuriumLabel), "CumulativeSelection")
                        .label(cselEpineuriumLabel);
                }

                String epineuriumXsLabel = "Epineurium Cross Section";
                GeomFeature epineuriumXs = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("wp", epineuriumXsLabel), "WorkPlane");
                epineuriumXs.label(epineuriumXsLabel);
                epineuriumXs.set("contributeto", im.get("EPIXS"));
                epineuriumXs.set("unite", true);
                epineuriumXs.geom().create("e1", "Ellipse");
                epineuriumXs
                    .geom()
                    .feature("e1")
                    .set("semiaxes", new String[] { "r_nerve", "r_nerve" });

                String epiLabel = "Make Epineurium";
                GeomFeature epi = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("ext", epiLabel), "Extrude");
                epi.label(epiLabel);
                epi.set("contributeto", im.get("EPINEURIUM"));
                epi.setIndex("distance", "z_nerve", 0);
                epi.selection("input").named(im.get("EPIXS"));

                // Add epi domains to ALL_NERVE_PARTS_UNION for later assigning to materials
                String[] epiUnions = { ModelWrapper.ALL_NERVE_PARTS_UNION };
                mw.contributeToUnions(im.get(epiLabel), epiUnions);

                break;
            case "Epi_trace":
                im.labels =
                    new String[] {
                        "EPINEURIUM", //0
                        "EPIXS",
                    };

                for (String cselEpineuriumLabel : im.labels) {
                    model
                        .component("comp1")
                        .geom("geom1")
                        .selection()
                        .create(im.next("csel", cselEpineuriumLabel), "CumulativeSelection")
                        .label(cselEpineuriumLabel);
                }
                //Generate plane for nerve trace
                String nervePlaneLabel = "Epineurium Geometry";
                GeomFeature nervePlane = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("wp", nervePlaneLabel), "WorkPlane");
                nervePlane.label(nervePlaneLabel);
                nervePlane.set("contributeto", im.get("EPIXS"));
                nervePlane.set("unite", true);

                //Add selections for the nerve object and nerve trace
                String nerveLabel = "nerve curve";
                nervePlane
                    .geom()
                    .selection()
                    .create(im.next("csel", nerveLabel), "CumulativeSelection");
                nervePlane.geom().selection(im.get(nerveLabel)).label(nerveLabel);
                String nerveselLabel = "nerve sel";
                nervePlane
                    .geom()
                    .selection()
                    .create(im.next("csel", nerveselLabel), "CumulativeSelection");
                nervePlane.geom().selection(im.get(nerveselLabel)).label(nerveselLabel);

                //Create nerve trace curve
                String nerve_trace_path = path + "/" + tracePaths.get("nerve")[0];
                String nerveic_label = "Epineurium Trace";
                GeomFeature nerveic = nervePlane
                    .geom()
                    .create(im.next("ic", nerveic_label), "InterpolationCurve");
                nerveic.label(nerveic_label);
                nerveic.set("contributeto", im.get(nerveLabel));
                nerveic.set("source", "file");
                nerveic.set("filename", nerve_trace_path);
                nerveic.set("type", "closed");

                //set interpolation tolerance for nerve curve
                if (modelData.has("nerve_interp_tol") && !modelData.has("trace_interp_tol")) {
                    nerveic.set("rtol", modelData.getDouble("nerve_interp_tol"));
                } else if (
                    modelData.has("trace_interp_tol") && !modelData.has("nerve_interp_tol")
                ) {
                    nerveic.set("rtol", modelData.getDouble("trace_interp_tol")); // backwards compatibility
                } else if (modelData.has("trace_interp_tol") && modelData.has("nerve_interp_tol")) {
                    throw new Exception(
                        "Both trace_interp_tol and nerve_interp_tol defined in Model. " +
                        "Use new convention for inners (outer_interp_tol), outers (outer_interp_tol), and nerve (nerve_interp_tol) separately!"
                    );
                } else {
                    throw new Exception(
                        "You must specify a nerve interpolation tolerance (nerve_interp_tol in model.json)"
                    );
                }

                //Generate surface from curve
                String nerveicSurfaceLabel = "Epineurium Outer Surface";
                nervePlane.geom().create(im.next("csol", nerveicSurfaceLabel), "ConvertToSolid");
                nervePlane.geom().feature(im.get(nerveicSurfaceLabel)).set("keep", false);
                nervePlane
                    .geom()
                    .feature(im.get(nerveicSurfaceLabel))
                    .selection("input")
                    .named(im.get(nerveLabel));
                nervePlane
                    .geom()
                    .feature(im.get(nerveicSurfaceLabel))
                    .set("contributeto", im.get(nerveselLabel));
                nervePlane.geom().feature(im.get(nerveicSurfaceLabel)).label(nerveicSurfaceLabel);

                //Extrude surface into 3D object
                String epi_tr_Label = "Make Epineurium";
                GeomFeature makeEpi = model
                    .component("comp1")
                    .geom("geom1")
                    .create(im.next("ext", epi_tr_Label), "Extrude");
                makeEpi.label(epi_tr_Label);
                makeEpi.set("contributeto", im.get("EPINEURIUM"));
                makeEpi.set("workplane", im.get(nervePlaneLabel));
                makeEpi.setIndex("distance", "z_nerve", 0);
                makeEpi.selection("input").named(im.get("EPIXS"));

                // Add epi domains to ALL_NERVE_PARTS_UNION for later assigning to materials
                String[] epi_trace_Unions = { ModelWrapper.ALL_NERVE_PARTS_UNION };
                mw.contributeToUnions(im.get(epi_tr_Label), epi_trace_Unions);

                break;
            default:
                throw new IllegalArgumentException(
                    "No implementation for part instance name: " + pseudonym
                );
        }
    }

    /**
     * Create a material!
     * @param materialID the material COMSOL id (unique) --> use mw.im.next in call (mat#)
     * @param modelData JSON data from master.json
     * @param mw the ModelWrapper to act upon
     */
    public static void defineMaterial(
        String materialID,
        String function,
        JSONObject modelData,
        JSONObject materialsData,
        ModelWrapper mw,
        ModelParamGroup materialParams
    ) {
        Model model = mw.getModel();
        model.material().create(materialID, "Common", "");
        model.material(materialID).label(function);

        JSONObject sigma = null;
        String materialDescription = null;

        // if the material is defined explicitly in the model.json file, then the program will use the value stored in
        // model.json (CUSTOM), otherwise it will use the conductivity value stored in the materials.json file (DEFAULT).
        // This ties material parameters used to a specific model.
        JSONObject model_conductivities = modelData.getJSONObject("conductivities");
        Object material_assignment = model_conductivities.get(function);

        if (material_assignment instanceof String) {
            // REFERENCING PRE-DEFINED MATERIAL IN JSON
            // Load in JSONObject from materials_config
            sigma =
                materialsData
                    .getJSONObject("conductivities")
                    .getJSONObject((String) material_assignment);
            materialDescription = "default: " + material_assignment;
        } else if (material_assignment instanceof JSONObject) {
            // CUSTOM DEFINED MATERIAL DIRECTLY IN MODEL CONFIG
            // Load in JSONObject from model_config
            sigma = (JSONObject) material_assignment;
            materialDescription = "custom: " + sigma.getString("label");
        }
        assert sigma != null;
        String entry = sigma.getString("value");
        String unit = sigma.getString("unit");

        if (entry.equals("anisotropic")) {
            String entry_x = sigma.getString("sigma_x");
            String entry_y = sigma.getString("sigma_y");
            String entry_z = sigma.getString("sigma_z");

            materialParams.set(
                "sigma_" + function + "_x",
                "(" + entry_x + ")",
                materialDescription
            );
            materialParams.set(
                "sigma_" + function + "_y",
                "(" + entry_y + ")",
                materialDescription
            );
            materialParams.set(
                "sigma_" + function + "_z",
                "(" + entry_z + ")",
                materialDescription
            );

            model
                .material(materialID)
                .propertyGroup("def")
                .set(
                    "electricconductivity",
                    new String[] {
                        "sigma_" + function + "_x",
                        "0",
                        "0",
                        "0",
                        "sigma_" + function + "_y",
                        "0",
                        "0",
                        "0",
                        "sigma_" + function + "_z",
                    }
                );
        } else {
            materialParams.set(
                "sigma_" + function,
                "(" + entry + ")" + " " + unit,
                materialDescription
            );
            model
                .material(materialID)
                .propertyGroup("def")
                .set("electricconductivity", "sigma_" + function);
        }
    }

    public static void addCuffPartMaterialAssignment(
        String instanceLabel,
        String pseudonym,
        ModelWrapper mw,
        JSONObject instanceParams
    ) throws IllegalArgumentException {
        Model model = mw.getModel();

        IdentifierManager myIM = mw.getPartPrimitiveIM(pseudonym);
        if (myIM == null) throw new IllegalArgumentException(
            "IdentfierManager not created for name: " + pseudonym
        );

        String[] myLabels = myIM.labels; // may be null, but that is ok if not used

        // assign cuff materials
        JSONArray materials = instanceParams.getJSONArray("materials");
        for (Object o : materials) {
            int label_index = ((JSONObject) o).getInt("label_index");
            String selection = myLabels[label_index];
            String info = ((JSONObject) o).getString("info");

            if (myIM.hasPseudonym(selection)) {
                String linkLabel = String.join(
                    "/",
                    new String[] { instanceLabel, selection, info }
                );
                Material mat = model
                    .component("comp1")
                    .material()
                    .create(mw.im.next("matlnk", linkLabel), "Link");
                mat.label(linkLabel);
                mat.set("link", mw.im.get(info));
                mat
                    .selection()
                    .named(
                        "geom1_" + mw.im.get(instanceLabel) + "_" + myIM.get(selection) + "_dom"
                    );
            }
        }
    }
}
