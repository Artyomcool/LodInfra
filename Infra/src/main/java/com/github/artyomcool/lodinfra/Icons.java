package com.github.artyomcool.lodinfra;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

public class Icons {

    public static Node expandIcon() {
        return icon(
                Color.grayRgb(0x60),
                "M435.197,153.593h-115.2v25.6h102.4v307.2h-332.8v-307.2h102.4v-25.6h-115.2c-7.066,0-12.8,5.734-12.8,12.8v332.8 c0,7.066,5.734,12.8,12.8,12.8h358.4c7.066,0,12.8-5.734,12.8-12.8v-332.8C447.997,159.328,442.262,153.593,435.197,153.593z M341.74,78.782l-76.8-75.136c-5.043-4.941-13.158-4.847-18.099,0.205l-76.595,74.923 c-5.052,4.949-5.146,13.047-0.205,18.108c4.941,5.035,13.056,5.129,18.099,0.188l55.057-53.854v275.098 c0,7.074,5.734,12.8,12.8,12.8c7.066,0,12.8-5.717,12.8-12.8V43.215l55.049,53.854c5.043,4.949,13.158,4.855,18.099-0.188 C346.885,91.821,346.8,83.722,341.74,78.782z",
                1d / 32
        );
    }

    public static Node downloadIcon() {
        return downloadIcon(Color.CORNFLOWERBLUE);
    }

    public static Node downloadIcon(Color color) {
        return icon(
                color,
                "M8.05,15.15H1.31C.52,15.15.2,14.83.2,14v-2.7a.9.9,0,0,1,1-1H5.27a.57.57,0,0,1,.37.16c.36.34.71.71,1.06,1.06a1.82,1.82,0,0,0,2.68,0c.36-.36.71-.73,1.09-1.08a.61.61,0,0,1,.37-.15h4a.92.92,0,0,1,1,1v2.79a.9.9,0,0,1-1,1Zm3.62-2.4a.6.6,0,1,0,0,1.19.6.6,0,1,0,0-1.19Zm1.82.61a.58.58,0,0,0,.61.58.6.6,0,1,0-.61-.58ZM6.23,5.5v-4c0-.6.2-.79.8-.79H9.11c.53,0,.74.21.75.74,0,1.25,0,2.51,0,3.76,0,.26.07.34.33.33.66,0,1.32,0,2,0a.63.63,0,0,1,.66.39.61.61,0,0,1-.2.71l-4.1,4.09a.6.6,0,0,1-1,0L3.48,6.61a.61.61,0,0,1-.21-.73.63.63,0,0,1,.66-.38h2.3Z"
        );
    }

    public static Node uploadIcon() {
        return icon(
                Color.GOLDENROD,
                "M16,9.64v.74a.54.54,0,0,0,0,.1,3.55,3.55,0,0,1-.46,1.35,3.68,3.68,0,0,1-3.35,1.89H3.37a3.49,3.49,0,0,1-1-.13A3.21,3.21,0,0,1,.07,10,3.13,3.13,0,0,1,1.51,7.78a.19.19,0,0,0,.1-.26,2,2,0,0,1,.1-1.43A2.2,2.2,0,0,1,4.3,4.82a.16.16,0,0,0,.22-.1,3.13,3.13,0,0,1,.19-.36A4.46,4.46,0,0,1,9.79,2.43a4.38,4.38,0,0,1,3.14,3.69c0,.16.1.2.23.24A3.63,3.63,0,0,1,15.8,8.79,5.85,5.85,0,0,1,16,9.64ZM7,9.18v1.94a.5.5,0,0,0,.55.56h.89A.5.5,0,0,0,9,11.12V9.36c0-.06,0-.11,0-.18h1a.48.48,0,0,0,.45-.29.49.49,0,0,0-.12-.53l-2-1.93a.49.49,0,0,0-.77,0l-2,1.93a.49.49,0,0,0-.12.53A.49.49,0,0,0,6,9.17H7Z"
        );
    }

    public static Node archiveIcon() {
        return icon(
            Color.web("#1C274C"),
                "M2 5C2 4.05719 2 3.58579 2.29289 3.29289C2.58579 3 3.05719 3 4 3H20C20.9428 3 21.4142 3 21.7071 3.29289C22 3.58579 22 4.05719 22 5C22 5.94281 22 6.41421 21.7071 6.70711C21.4142 7 20.9428 7 20 7H4C3.05719 7 2.58579 7 2.29289 6.70711C2 6.41421 2 5.94281 2 5Z M20.0689 8.49993C20.2101 8.49999 20.3551 8.50005 20.5 8.49805V12.9999C20.5 16.7711 20.5 18.6568 19.3284 19.8283C18.1569 20.9999 16.2712 20.9999 12.5 20.9999H11.5C7.72876 20.9999 5.84315 20.9999 4.67157 19.8283C3.5 18.6568 3.5 16.7711 3.5 12.9999V8.49805C3.64488 8.50005 3.78999 8.49999 3.93114 8.49993H20.0689ZM9 11.9999C9 11.5339 9 11.301 9.07612 11.1172C9.17761 10.8722 9.37229 10.6775 9.61732 10.576C9.80109 10.4999 10.0341 10.4999 10.5 10.4999H13.5C13.9659 10.4999 14.1989 10.4999 14.3827 10.576C14.6277 10.6775 14.8224 10.8722 14.9239 11.1172C15 11.301 15 11.5339 15 11.9999C15 12.4658 15 12.6988 14.9239 12.8826C14.8224 13.1276 14.6277 13.3223 14.3827 13.4238C14.1989 13.4999 13.9659 13.4999 13.5 13.4999H10.5C10.0341 13.4999 9.80109 13.4999 9.61732 13.4238C9.37229 13.3223 9.17761 13.1276 9.07612 12.8826C9 12.6988 9 12.4658 9 11.9999Z",
                2d / 3
        );
    }

    public static Node box() {
        return icon(
                Color.web("#1C274C"),
                "M17.5777 4.43152L15.5777 3.38197C13.8221 2.46066 12.9443 2 12 2C11.0557 2 10.1779 2.46066 8.42229 3.38197L6.42229 4.43152C4.64855 5.36234 3.6059 5.9095 2.95969 6.64132L12 11.1615L21.0403 6.64132C20.3941 5.9095 19.3515 5.36234 17.5777 4.43152Z M21.7484 7.96435L12.75 12.4635V21.904C13.4679 21.7252 14.2848 21.2965 15.5777 20.618L17.5777 19.5685C19.7294 18.4393 20.8052 17.8748 21.4026 16.8603C22 15.8458 22 14.5833 22 12.0585V11.9415C22 10.0489 22 8.86558 21.7484 7.96435Z M11.25 21.904V12.4635L2.25164 7.96434C2 8.86557 2 10.0489 2 11.9415V12.0585C2 14.5833 2 15.8458 2.5974 16.8603C3.19479 17.8748 4.27063 18.4393 6.42229 19.5685L8.42229 20.618C9.71524 21.2965 10.5321 21.7252 11.25 21.904Z",
                2d / 3
        );
    }

    public static Node cursor() {
        return icon(
                Color.web("#1C274C"),
                "M3.46447 3.46447C2 4.92893 2 7.28595 2 12C2 16.714 2 19.0711 3.46447 20.5355C4.92893 22 7.28595 22 12 22C16.714 22 19.0711 22 20.5355 20.5355C22 19.0711 22 16.714 22 12C22 7.28595 22 4.92893 20.5355 3.46447C19.0711 2 16.714 2 12 2C7.28595 2 4.92893 2 3.46447 3.46447ZM12.3975 14.0385L14.859 16.4999C15.1138 16.7548 15.2413 16.8822 15.3834 16.9411C15.573 17.0196 15.7859 17.0196 15.9755 16.9411C16.1176 16.8822 16.2451 16.7548 16.4999 16.4999C16.7548 16.2451 16.8822 16.1176 16.9411 15.9755C17.0196 15.7859 17.0196 15.573 16.9411 15.3834C16.8822 15.2413 16.7548 15.1138 16.4999 14.859L14.0385 12.3975L14.7902 11.6459C15.5597 10.8764 15.9444 10.4916 15.8536 10.0781C15.7628 9.66451 15.2522 9.47641 14.231 9.10019L10.8253 7.84544C8.78816 7.09492 7.7696 6.71966 7.24463 7.24463C6.71966 7.7696 7.09492 8.78816 7.84544 10.8253L9.10019 14.231C9.47641 15.2522 9.66452 15.7628 10.0781 15.8536C10.4916 15.9444 10.8764 15.5597 11.6459 14.7902L12.3975 14.0385Z"
        );
    }

    public static Node document() {
        return icon(
                Color.web("#1C274C"),
                "M4.17157 3.17157C3 4.34315 3 6.22876 3 10V14C3 17.7712 3 19.6569 4.17157 20.8284C5.34315 22 7.22876 22 11 22H13C16.7712 22 18.6569 22 19.8284 20.8284C21 19.6569 21 17.7712 21 14V10C21 6.22876 21 4.34315 19.8284 3.17157C18.6569 2 16.7712 2 13 2H11C7.22876 2 5.34315 2 4.17157 3.17157ZM7.25 8C7.25 7.58579 7.58579 7.25 8 7.25H16C16.4142 7.25 16.75 7.58579 16.75 8C16.75 8.41421 16.4142 8.75 16 8.75H8C7.58579 8.75 7.25 8.41421 7.25 8ZM7.25 12C7.25 11.5858 7.58579 11.25 8 11.25H16C16.4142 11.25 16.75 11.5858 16.75 12C16.75 12.4142 16.4142 12.75 16 12.75H8C7.58579 12.75 7.25 12.4142 7.25 12ZM8 15.25C7.58579 15.25 7.25 15.5858 7.25 16C7.25 16.4142 7.58579 16.75 8 16.75H13C13.4142 16.75 13.75 16.4142 13.75 16C13.75 15.5858 13.4142 15.25 13 15.25H8Z",
                2d / 3
        );
    }

    public static Node flag() {
        return icon(
                Color.web("#1C274C"),
                "M5.75 1C6.16421 1 6.5 1.33579 6.5 1.75V3.6L8.22067 3.25587C9.8712 2.92576 11.5821 3.08284 13.1449 3.70797L13.5582 3.87329C14.9831 4.44323 16.5513 4.54967 18.0401 4.17746C18.6711 4.01972 19.1778 4.7036 18.8432 5.26132L17.5647 7.39221C17.2232 7.96137 17.0524 8.24595 17.0119 8.55549C16.9951 8.68461 16.9951 8.81539 17.0119 8.94451C17.0524 9.25405 17.2232 9.53863 17.5647 10.1078L19.1253 12.7089C19.4361 13.2269 19.1582 13.898 18.5721 14.0445L18.472 14.0695C16.7024 14.5119 14.8385 14.3854 13.1449 13.708C11.5821 13.0828 9.8712 12.9258 8.22067 13.2559L6.5 13.6V21.75C6.5 22.1642 6.16421 22.5 5.75 22.5C5.33579 22.5 5 22.1642 5 21.75V1.75C5 1.33579 5.33579 1 5.75 1Z",
                2d / 3
        );
    }

    public static Node inProgress() {
        return icon(
                Color.GRAY,
                "M6.3012,1.8372V1.3056a4.25,4.25,0,0,1-.6188-.1951A.4923.4923,0,0,1,5.4218.4671c.07-.1785.2695-.422.4242-.4318A17.616,17.616,0,0,1,8.0319.0326c.1576.01.3608.2449.4348.4216a.4914.4914,0,0,1-.2468.6489,4.0188,4.0188,0,0,1-.6321.1987v.5293A8.8925,8.8925,0,0,1,11.4027,3.41a8.7185,8.7185,0,0,1,.6712-.6037.6149.6149,0,0,1,.8576.0607.6035.6035,0,0,1,.0129.79c-.17.2266-.3777.4246-.6269.6993a7.0381,7.0381,0,0,1,1.4581,5.7052,6.6624,6.6624,0,0,1-2.0506,3.7858,7.0427,7.0427,0,0,1-8.1227,1.0278A6.7713,6.7713,0,0,1,.1234,7.5634C.6646,4.625,2.522,2.5213,6.3012,1.8372ZM9.4894,5.62C9,6.0969,8.6445,6.4473,8.2845,6.7934A2.7859,2.7859,0,0,1,6.6964,7.825a.9486.9486,0,0,0-.6842,1.38,1.1338,1.1338,0,0,0,.9734.6569,1.1256,1.1256,0,0,0,1.0049-.9912,1.2185,1.2185,0,0,1,.49-.9466l1.6334-1.667Z",
                2d / 3
        );
    }

    public static Node observe() {
        return icon(
                Color.DODGERBLUE,
                "M8,4.753a.5651.5651,0,0,1,.0654.0044.653.653,0,0,1,.5163.3615c.0977.5194.0833,1.0584.1338,1.5877a3.54,3.54,0,0,0,3.0383,3.2617l.0173.0019c.0755.0105.1524.0156.2291.0213.1136.0063.2285.01.346.0078a3.6265,3.6265,0,0,0,3.6175-3.4578c.2663-2.357-.976-4.1-2.2977-5.77A1.8961,1.8961,0,0,0,12.2469,0a1.8758,1.8758,0,0,0-.98.31c-.2978.1984-.44.6263-.6522.95.2478.091.5275.2768.7643.3354a1.4169,1.4169,0,0,1,.7629-.0893,1.2147,1.2147,0,0,1,1.0743.8038c.2689.5091.5485,1.0125.9425,1.7378-.0762-.003-.1515-.0032-.2271-.0041q-.0684-.0015-.1363-.0021c-.7513.0016-1.4689.0853-2.1595.0894a3.959,3.959,0,0,1-2.811-.8182A1.4573,1.4573,0,0,0,8,3.0905a1.4573,1.4573,0,0,0-.8251.2214,3.959,3.959,0,0,1-2.811.8182c-.6906-.0041-1.4082-.0878-2.1595-.0894q-.068.0006-.1363.0021c-.0756.0009-.1509.0011-.2271.0041.394-.7253.6736-1.2287.9425-1.7378a1.2147,1.2147,0,0,1,1.0743-.8038,1.4169,1.4169,0,0,1,.7629.0893c.2368-.0586.5165-.2444.7643-.3354C5.1723.9359,5.0306.508,4.7328.31a1.8758,1.8758,0,0,0-.98-.31A1.8961,1.8961,0,0,0,2.334.771C1.0123,2.4415-.23,4.1845.0363,6.5415A3.6265,3.6265,0,0,0,3.6538,9.9993c.1175.0023.2324-.0015.346-.0078.0767-.0057.1536-.0108.2291-.0213l.0173-.0019A3.54,3.54,0,0,0,7.2845,6.7066c.05-.5293.0361-1.0683.1338-1.5877a.653.653,0,0,1,.5163-.3615A.5651.5651,0,0,1,8,4.753Z",
                2d / 3
        );
    }

    public static Node shield() {
        return icon(
                Color.DODGERBLUE,
                "M3.37752 5.08241C3 5.62028 3 7.21907 3 10.4167V11.9914C3 17.6294 7.23896 20.3655 9.89856 21.5273C10.62 21.8424 10.9807 22 12 22C13.0193 22 13.38 21.8424 14.1014 21.5273C16.761 20.3655 21 17.6294 21 11.9914V10.4167C21 7.21907 21 5.62028 20.6225 5.08241C20.245 4.54454 18.7417 4.02996 15.7351 3.00079L15.1623 2.80472C13.595 2.26824 12.8114 2 12 2C11.1886 2 10.405 2.26824 8.83772 2.80472L8.26491 3.00079C5.25832 4.02996 3.75503 4.54454 3.37752 5.08241ZM10.8613 8.36335L10.7302 8.59849C10.5862 8.85677 10.5142 8.98591 10.402 9.07112C10.2897 9.15633 10.1499 9.18796 9.87035 9.25122L9.61581 9.30881C8.63194 9.53142 8.14001 9.64273 8.02297 10.0191C7.90593 10.3955 8.2413 10.7876 8.91204 11.572L9.08557 11.7749C9.27617 11.9978 9.37147 12.1092 9.41435 12.2471C9.45722 12.385 9.44281 12.5336 9.41399 12.831L9.38776 13.1018C9.28635 14.1482 9.23565 14.6715 9.54206 14.9041C9.84847 15.1367 10.3091 14.9246 11.2303 14.5005L11.4686 14.3907C11.7304 14.2702 11.8613 14.2099 12 14.2099C12.1387 14.2099 12.2696 14.2702 12.5314 14.3907L12.7697 14.5005C13.6909 14.9246 14.1515 15.1367 14.4579 14.9041C14.7644 14.6715 14.7136 14.1482 14.6122 13.1018L14.586 12.831C14.5572 12.5337 14.5428 12.385 14.5857 12.2471C14.6285 12.1092 14.7238 11.9978 14.9144 11.7749L15.088 11.572C15.7587 10.7876 16.0941 10.3955 15.977 10.0191C15.86 9.64273 15.3681 9.53142 14.3842 9.30881L14.1296 9.25122C13.8501 9.18796 13.7103 9.15633 13.598 9.07112C13.4858 8.98592 13.4138 8.85678 13.2698 8.5985L13.1387 8.36335C12.6321 7.45445 12.3787 7 12 7C11.6213 7 11.3679 7.45445 10.8613 8.36335Z",
                2d / 3
        );
    }

    private static Node icon(Color color, String svg) {
        return icon(color, svg, 1);
    }

    private static Node icon(Color color, String svg, double scale) {
        SVGPath path = new SVGPath();
        path.setFill(color);
        path.setStroke(null);
        path.setContent(svg);
        path.setScaleX(scale);
        path.setScaleY(scale);
        path.setFillRule(FillRule.EVEN_ODD);
        return new Group(path);
    }
}
