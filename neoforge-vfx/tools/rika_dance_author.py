"""Dedicated spirit-cat boxing dance; continuous curved arms retain the artist UVs."""
import copy
import math
import numpy as np


def pulse(cycle):
    u = cycle % 1
    t = u / .20 if u < .20 else (u - .20) / .80
    s = t * t * (3 - 2 * t)
    return s if u < .20 else 1 - s


def cube_surface(cube, side):
    x,y,z = cube["from"]
    X,Y,Z = cube["to"]
    u,v,U,V = cube["faces"][side]["uv"]
    return {
        "north": ([(x,Y,z),(X,Y,z),(X,y,z),(x,y,z)],[(U,v),(u,v),(u,V),(U,V)]),
        "south": ([(X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)],[(U,V),(U,v),(u,v),(u,V)]),
        "east": ([(X,Y,z),(X,Y,Z),(X,y,Z),(X,y,z)],[(U,v),(u,v),(u,V),(U,V)]),
        "west": ([(x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)],[(U,V),(U,v),(u,v),(u,V)]),
        "up": ([(x,Y,Z),(X,Y,Z),(X,Y,z),(x,Y,z)],[(u,V),(U,V),(U,v),(u,v)]),
        "down": ([(X,y,z),(X,y,Z),(x,y,Z),(x,y,z)],[(U,V),(U,v),(u,v),(u,V)]),
    }[side]


def curved_cube(cube, deform, step=.5):
    """Subdivide one closed source skin and carry its original per-face UVs."""
    mesh = copy.deepcopy(cube)
    mesh["rika_original_cube"] = copy.deepcopy(cube)
    mesh["type"] = "mesh"
    mesh["vertices"], mesh["faces"] = {}, {}
    origin = np.array(cube["origin"])
    lookup = {}
    def vertex(p):
        key = tuple(round(float(v), 7) for v in p)
        if key in lookup:
            return lookup[key]
        local = deform(np.array(p))-origin
        identity = "v"+str(len(lookup))
        mesh["vertices"][identity] = [round(float(v),7) for v in local]
        lookup[key] = identity
        return identity
    for side, face in cube["faces"].items():
        points, uvs = cube_surface(cube,side)
        p,uv = np.array(points),np.array(uvs)
        nu = max(1,round(max(abs(p[1,1]-p[0,1]),abs(p[2,1]-p[3,1]))/step))
        nv = max(1,round(max(abs(p[3,1]-p[0,1]),abs(p[2,1]-p[1,1]))/step))
        def lerp(q,a,b):
            return q[0]*(1-a)*(1-b)+q[1]*a*(1-b)+q[2]*a*b+q[3]*(1-a)*b
        for j in range(nv):
            for i in range(nu):
                corners = [(i/nu,j/nv),((i+1)/nu,j/nv),((i+1)/nu,(j+1)/nv),(i/nu,(j+1)/nv)]
                ids = [vertex(lerp(p,a,b)) for a,b in corners]
                mesh["faces"][side+str(i)+"_"+str(j)] = {
                    "vertices":ids,"uv":{identity:list(lerp(uv,a,b)) for identity,(a,b) in zip(ids,corners)},
                    "texture":face["texture"],
                }
    for key in ["from","to","box_uv"]:
        mesh.pop(key,None)
    return mesh


def curved_arm(cube, groups):
    # Author the silhouette in the posed rig, then return it to local space.
    # Define the guard in the head's own forward frame. World-space offsets
    # can put the near fist behind the ear after the torso/head counter-rotate.
    from import_rika_cat import matrix
    root = np.array(groups['group5']['origin'])
    basis = matrix(root, ROOT_POSE)
    inverse = np.linalg.inv(basis)
    lo, hi = np.array(cube['from']), np.array(cube['to'])
    center = (lo+hi)/2
    direction = 1 if center[0] > 0 else -1
    shoulder = (basis @ np.r_[np.array([center[0],hi[1],center[2]])-root,1])[:3]
    head = np.array(groups['head']['origin'])
    head_basis = basis @ matrix(head-root, [-9,8,9])
    # Fists guard the lower cheeks, outside the five-pixel-wide muzzle.
    # The previous inward/high endpoint made the forearms cover the forehead:
    # forward distance alone is insufficient for a tilted head's silhouette.
    # Tuck the inside guard slightly: Rika stands immediately behind the other
    # dancer, so mirroring the wide outer elbow would put it through their face.
    elbow_x, wrist_x = (4.4,2.6) if direction > 0 else (6.2,3.8)
    elbow = (head_basis @ np.r_[np.array([direction*elbow_x,4.9,-7.7])-head,1])[:3]
    wrist_z = -13.0 if direction > 0 else -13.4
    wrist_y = 8.0 if direction > 0 else 8.5
    wrist = (head_basis @ np.r_[np.array([direction*wrist_x,wrist_y,wrist_z])-head,1])[:3]
    # The bend radius must exceed the skin's half-diagonal. The former short
    # corner compressed the inner surface through itself even though it was closed.
    a, b = elbow+(shoulder-elbow)*.45, elbow+(wrist-elbow)*.45
    down=-basis[:3,1]
    def curve(u):
        if u < .40:
            t=u/.40;return shoulder*(1-t)+a*t,a-shoulder
        if u < .65:
            t=(u-.40)/.25
            return a*(1-t)**2+elbow*2*t*(1-t)+b*t*t, 2*((elbow-a)*(1-t)+(b-elbow)*t)
        t=(u-.65)/.35;return b*(1-t)+wrist*t, wrist-b
    def transport(vector,tangent):
        # A shortest-arc rotation remains well-defined as the arm crosses the
        # world Z axis. cross(worldZ,tangent) flipped the old skin at the elbow.
        axis=np.cross(down,tangent);cosine=np.dot(down,tangent)
        return vector+np.cross(axis,vector)+np.cross(axis,np.cross(axis,vector))/(1+cosine)
    end_tangent=(wrist-b)/np.linalg.norm(wrist-b)
    end_normal=np.cross([0,0,1],end_tangent);end_normal/=np.linalg.norm(end_normal)
    transported=transport(basis[:3,0],end_tangent)
    twist=math.atan2(np.dot(end_tangent,np.cross(transported,end_normal)),np.dot(transported,end_normal))
    # The square paw is unchanged by quarter turns. Select its equivalent frame
    # with the least roll, rather than winding the forearm around its own axis.
    twist=(twist+math.pi/4)%(math.pi/2)-math.pi/4
    def deform(p):
        u=(hi[1]-p[1])/(hi[1]-lo[1])
        point,tangent=curve(u);tangent/=np.linalg.norm(tangent)
        if u<.40:
            axis=np.cross(down,tangent);axis/=np.linalg.norm(axis)
            blend=u/.40;angle=math.acos(np.clip(np.dot(down,tangent),-1,1))*blend*blend*(3-2*blend)
            tangent=down*math.cos(angle)+np.cross(axis,down)*math.sin(angle)
        normal=transport(basis[:3,0],tangent)
        # Spread the small remaining roll across the skin, keeping every section
        # orthonormal instead of linearly blending it through a collapsed plane.
        angle=twist*u*u*(3-2*u)
        normal=normal*math.cos(angle)+np.cross(tangent,normal)*math.sin(angle)
        depth=np.cross(tangent,normal)
        point=point+normal*(p[0]-center[0])+depth*(p[2]-center[2])
        return (inverse @ np.r_[point,1])[:3]+root
    return curved_cube(cube,deform,.25)


ROOT_POSE = [30., -22., -24.]


def curl_spirit_taper(model, groups):
    """Curl the substantial dark spirit tail, not the separate thin cat tail."""
    from import_rika_cat import matrix
    root = np.array(groups['group5']['origin'])
    body = np.array(groups['body']['origin'])
    basis = matrix(root,ROOT_POSE) @ matrix(body-root,groups['body']['rotation'])
    inverse = np.linalg.inv(basis)
    start = (basis @ np.r_[np.array([0.,0.,-15.8])-body,1])[:3]
    # A front-readable, gently oblique oval. Start at its left edge, descend,
    # curl beneath the belly and turn the tip upward, leaving a visible opening.
    ex = np.array([math.cos(math.radians(12)),0,math.sin(math.radians(12))])
    ey = np.array([0.,1.,0.]); ez = np.cross(ex,ey)
    rx,ry = 3.8,4.6
    center = start+rx*ex
    fractions = [0.,5.04/11.8944,9.072/11.8944,1.]
    widths,depths = [1.44,1.15,.805,.48],[2.16,1.728,1.21,.60]
    by_uuid = {e['uuid']:i for i,e in enumerate(model['elements'])}
    nodes = {}
    def visit(node):
        if isinstance(node,dict):
            nodes[node['uuid']] = node
            for child in node['children']:visit(child)
    for node in model['outliner']:visit(node)
    for segment,name in enumerate(['group46','group47','group48']):
        group = groups[name]
        group['rotation'] = [0,0,0]
        identity = next(n for n in nodes[group['uuid']]['children'] if isinstance(n,str))
        i = by_uuid[identity]
        cube = model['elements'][i].get('rika_original_cube',model['elements'][i])
        lo,hi = np.array(cube['from']),np.array(cube['to'])
        def deform(p,segment=segment,lo=lo,hi=hi):
            t = (hi[1]-p[1])/(hi[1]-lo[1])
            u = fractions[segment]*(1-t)+fractions[segment+1]*t
            theta = math.pi+math.radians(280)*u
            position = center+rx*math.cos(theta)*ex+ry*math.sin(theta)*ey
            normal = -((math.cos(theta)/rx)*ex+(math.sin(theta)/ry)*ey)
            normal /= np.linalg.norm(normal)
            halfwidth = widths[segment]*(1-t)+widths[segment+1]*t
            halfdepth = depths[segment]*(1-t)+depths[segment+1]*t
            point = position+normal*((2*(p[0]-lo[0])/(hi[0]-lo[0])-1)*halfwidth)+ez*((2*(p[2]-lo[2])/(hi[2]-lo[2])-1)*halfdepth)
            return (inverse @ np.r_[point,1])[:3]+body
        model['elements'][i] = curved_cube(cube,deform,.20)


def author_dance(model, add_key, faces, period):
    groups = {g["name"]:g for g in model["groups"]}
    curl_spirit_taper(model,groups)
    for i,e in enumerate(model["elements"]):
        if e["name"] in ("left_front_leg_cube","right_front_leg_cube"):
            model["elements"][i] = curved_arm(e.get("rika_original_cube",e),groups)
    tracks = {}
    count = 440  # Every 20% push peak lies on a key for both 8x and 4x pulses.
    for frame in range(count+1):
        cycle = frame/count
        time = period*cycle
        bounce,opened,sway = pulse(cycle*8),pulse(cycle*4),math.sin(cycle*math.pi*8)
        values = {
            # Lean forward and inward as one attached rig, rather than standing
            # upright in a separate lane. Head counter-motion keeps the face open.
            "group5":[ROOT_POSE[0]+2*bounce,ROOT_POSE[1],ROOT_POSE[2]+1.3*sway],
            "head":[-24-2.5*bounce,8-2*sway,9-1.3*sway],
            "body":[0,0,0],
            "left_front_leg":[-2.58*bounce,0,8*opened],
            "right_front_leg":[-2.58*bounce,0,-8*opened],
            "group3":[-30+3*bounce,5*sway,0],
            "group4":[18-4*bounce,5*sway,0],
            "group45":[3+2*bounce,0,0],
            "group46":[0,0,0],
            "group47":[0,0,0],
            "group48":[0,0,0],
        }
        for name,value in values.items():
            add_key(tracks,groups[name],"rotation",time,value)
    clip = {"name":"animation.rika.dance","loop":"loop","length":period,"animators":tracks}
    # This artist's spirit has no hind legs. Its taper and curled tail form the
    # supporting base; both real front paws stay raised together as boxing fists.
    floors = []
    for frame in range(count+1):
        cycle,time = frame/count,period*frame/count
        floor = min(p[:,1].min() for _,p,_ in faces(model,clip,time))
        floors.append((cycle,time,floor))
    for cycle,time,floor in floors:
        add_key(tracks,groups["group2"],"position",time,[-.25*math.sin(cycle*math.pi*8),-floor+.7*pulse(cycle*8),0])
    model["animations"] = [clip]
    model["rika_dance_design"] = {
        "reference":"YutaDanceMotion: 88/30 seconds, 8 bounce pulses, 4 outward elbow pulses",
        "support":"Taper/tail base touches floor between hops; the supplied cat has no hind legs.",
        "arms":"Original skins follow continuous rounded elbow paths; both fists stay in front of the face in the head's own frame, with elbows outside and below. Original UVs retained.",
        "composition":"Close behind-right stagger; 30 degree forward rise, 24 degree inward lean, 22 degree inward yaw.",
        "tail":"Three original lower-body skins form one connected 280 degree oval coil; a visible hole remains beneath the belly. Thin cat tail kept separately.",
    }
    return model
