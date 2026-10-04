package org.marj4n.smooth_fix.performance;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

/** Same Z/Y/X quaternion arithmetic, held in scalar locals instead of a temporary heap object. */
public final class DirectModelRotation {
    private DirectModelRotation() { }

    public static void rotate(MatrixStack stack, float z, float y, float x) {
        MatrixStack.Entry entry = stack.peek();
        rotate(entry.getPositionMatrix(), entry.getNormalMatrix(), z, y, x);
    }

    public static void rotate(Matrix4f p, Matrix3f n, float z, float y, float x) {
        if (!Float.isFinite(z) || !Float.isFinite(y) || !Float.isFinite(x)) {
            Quaternionf original = new Quaternionf().rotationZYX(z, y, x);
            p.rotate(original);
            n.rotate(original);
            return;
        }
        float sz = org.joml.Math.sin(z * 0.5f), cz = org.joml.Math.cosFromSin(sz, z * 0.5f);
        float sy = org.joml.Math.sin(y * 0.5f), cy = org.joml.Math.cosFromSin(sy, y * 0.5f);
        float sx = org.joml.Math.sin(x * 0.5f), cx = org.joml.Math.cosFromSin(sx, x * 0.5f);
        float qx = cz * cy * sx - sz * sy * cx;
        float qy = cz * sy * cx + sz * cy * sx;
        float qz = sz * cy * cx - cz * sy * sx;
        float qw = cz * cy * cx + sz * sy * sx;
        float xx = qx*qx, yy = qy*qy, zz = qz*qz, ww = qw*qw;
        float xy = qx*qy, xz = qx*qz, xw = qx*qw, yz = qy*qz, yw = qy*qw, zw = qz*qw;
        float a = ww+xx-zz-yy, b = xy+zw+zw+xy, c = xz-yw+xz-yw;
        float d = xy-zw+xy-zw, e = yy-zz+ww-xx, f = yz+yz+xw+xw;
        float g = yw+xz+xz+yw, h = yz+yz-xw-xw, i = zz-yy-xx+ww;
        int properties = p.properties();
        float p00 = dot(p.m00(),a,p.m10(),b,p.m20(),c), p01 = dot(p.m01(),a,p.m11(),b,p.m21(),c);
        float p02 = dot(p.m02(),a,p.m12(),b,p.m22(),c), p03 = dot(p.m03(),a,p.m13(),b,p.m23(),c);
        float p10 = dot(p.m00(),d,p.m10(),e,p.m20(),f), p11 = dot(p.m01(),d,p.m11(),e,p.m21(),f);
        float p12 = dot(p.m02(),d,p.m12(),e,p.m22(),f), p13 = dot(p.m03(),d,p.m13(),e,p.m23(),f);
        float p20 = dot(p.m00(),g,p.m10(),h,p.m20(),i), p21 = dot(p.m01(),g,p.m11(),h,p.m21(),i);
        float p22 = dot(p.m02(),g,p.m12(),h,p.m22(),i), p23 = dot(p.m03(),g,p.m13(),h,p.m23(),i);
        p.m00(p00).m01(p01).m02(p02).m03(p03).m10(p10).m11(p11).m12(p12).m13(p13)
                .m20(p20).m21(p21).m22(p22).m23(p23);
        p.assume(properties & (Matrix4fc.PROPERTY_AFFINE | Matrix4fc.PROPERTY_ORTHONORMAL));
        float n00 = dot(n.m00(),a,n.m10(),b,n.m20(),c), n01 = dot(n.m01(),a,n.m11(),b,n.m21(),c);
        float n02 = dot(n.m02(),a,n.m12(),b,n.m22(),c), n10 = dot(n.m00(),d,n.m10(),e,n.m20(),f);
        float n11 = dot(n.m01(),d,n.m11(),e,n.m21(),f), n12 = dot(n.m02(),d,n.m12(),e,n.m22(),f);
        float n20 = dot(n.m00(),g,n.m10(),h,n.m20(),i), n21 = dot(n.m01(),g,n.m11(),h,n.m21(),i);
        float n22 = dot(n.m02(),g,n.m12(),h,n.m22(),i);
        n.set(n00,n01,n02,n10,n11,n12,n20,n21,n22);
    }

    private static float dot(float a, float b, float c, float d, float e, float f) {
        return org.joml.Math.fma(a, b, org.joml.Math.fma(c, d, e*f));
    }
}
